package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.PricelistFinder;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.web.dtos.AddItemsForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferItemsDraftTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private PricelistFinder pricelistFinder;
    @Mock
    private Inventory inventory;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private ProductCatalogRepository productCatalogRepository;

    @InjectMocks
    private OfferItemsDraft draft;

    @Test
    void anEntryNoLongerInThePricelistIsSkippedAndCounted() {
        // given
        Basket basket = basket(true);
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-1")).thenReturn(Optional.of(entry("pim-1", "MFN-1", "Laptops")));
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-gone")).thenReturn(Optional.empty());

        // when
        int skipped = draft.add(basket, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-1", 1),
                AddItemsForm.Entry.fromPricelist("cat-1", "pim-gone", 1)));

        // then
        assertThat(skipped).isEqualTo(1);
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-1");
    }

    @Test
    void anInventoryEntryNotFoundIsSkipped() {
        // given
        Basket basket = basket(true);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(null);

        // when
        int skipped = draft.add(basket, form(AddItemsForm.Entry.fromInventory("EAN-X", "MFN-X", 1)));

        // then
        assertThat(skipped).isEqualTo(1);
        assertThat(basket.getBasketItems()).isEmpty();
    }

    @Test
    void anInventoryEntryWithoutOffersAndWithoutACodeIsSkipped() {
        // given
        Basket basket = basket(true);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(MatchedInventory.empty(new InventoryKey("EAN-X", "")));

        // when
        int skipped = draft.add(basket, form(AddItemsForm.Entry.fromInventory("EAN-X", "", 1)));

        // then
        assertThat(skipped).isEqualTo(1);
        assertThat(basket.getBasketItems()).isEmpty();
    }

    @Test
    void entriesAreAddedAsBefore() {
        // given
        Basket basket = basket(false);
        basket.setBasketItems(new ArrayList<>(List.of(item("MFN-PSU", "PSU"))));
        when(pricelistFinder.findByPimId(STORE_ID, "cat-1", "pim-case"))
                .thenReturn(Optional.of(entry("pim-case", "MFN-CASE", "Case")));
        when(productCatalogRepository.findById(STORE_ID, "cat-1"))
                .thenReturn(catalog(definition("Case", 1), definition("PSU", 5)));
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
        when(inventoryView.findByInventoryKey(any())).thenReturn(MatchedInventory.empty(new InventoryKey("", "MFN-INV")));

        // when
        int skipped = draft.add(basket, form(AddItemsForm.Entry.fromPricelist("cat-1", "pim-case", 2),
                AddItemsForm.Entry.fromInventory("", "MFN-INV", 1)));

        // then
        assertThat(skipped).isZero();
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).startsWith("MFN-CASE", "MFN-PSU")
                .contains("MFN-INV");
        assertThat(basket.getBasketItems().get(0).getQty()).isEqualTo(2);
        assertThat(basket.getBasketItems()).filteredOn(i -> !i.getMfn().equals("MFN-PSU"))
                .allMatch(BasketItem::isConsolidated);
    }

    private static Basket basket(boolean showPrices) {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId("offer-1");
        basket.setShowPrices(showPrices);
        basket.setBasketItems(new ArrayList<>());
        return basket;
    }

    private static AvailabilityAndPrice entry(String pimId, String mfn, String category) {
        return new AvailabilityAndPrice(pimId, "EAN-" + pimId, mfn, "Brand", "Group", "Product " + pimId, category,
                199L, 1L, 3, 0L, false);
    }

    private static BasketItem item(String mfn, String category) {
        return new BasketItem("id-" + mfn, "name", mfn, category, 100.0, 0, 1, null, 3, false);
    }

    private static AddItemsForm form(AddItemsForm.Entry... entries) {
        AddItemsForm form = new AddItemsForm();
        form.setItems(List.of(entries));
        return form;
    }

    private static ProductCatalog catalog(CategoryDefinition... definitions) {
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "catalog");
        catalog.setCategories(List.of(definitions));
        return catalog;
    }

    private static CategoryDefinition definition(String category, int sequenceNumber) {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("def-" + category);
        definition.setName(category);
        definition.setCategory(category);
        definition.setSequenceNumber(sequenceNumber);
        return definition;
    }
}
