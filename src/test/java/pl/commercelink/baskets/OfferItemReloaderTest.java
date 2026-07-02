package pl.commercelink.baskets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferItemReloaderTest {

    private static final String STORE_ID = "store-1";
    private static final String BASKET_ID = "basket-1";

    @Mock
    private Inventory inventory;
    @Mock
    private PricelistRepository pricelistRepository;
    @Mock
    private BasketsRepository basketsRepository;
    @Mock
    private InventoryView inventoryView;

    @InjectMocks
    private OfferItemReloader offerItemReloader;

    @BeforeEach
    void setupInventory() {
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.PRICING)).thenReturn(inventoryView);
        when(inventoryView.findByProductCode(any())).thenReturn(MatchedInventory.empty(new InventoryKey()));
    }

    @Test
    @DisplayName("recalculate refreshes basket item unit price and mfn from current pricelist")
    void recalculateRefreshesBasketItemUnitPriceFromCurrentPricelist() {
        // given
        BasketItem item = basketItemWithCatalog("pim-1", "cat-1", "OLD-MFN", 100.0);
        Basket basket = basketWith(item);
        AvailabilityAndPrice freshPriceData = new AvailabilityAndPrice(
                "pim-1", "EAN-1", "NEW-MFN", "Brand", "Label", "Test Product",
                ProductCategory.Laptops, 250L, 5L, 3, 0L);
        Pricelist pricelist = new Pricelist("pricelist-1", List.of(freshPriceData));
        when(pricelistRepository.findNewestPricelistIdCached(STORE_ID, "cat-1")).thenReturn("pricelist-1");
        when(pricelistRepository.find(STORE_ID, "cat-1", "pricelist-1")).thenReturn(pricelist);
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));

        // when
        offerItemReloader.recalculate(basket);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        BasketItem savedItem = basketCaptor.getValue().getBasketItems().get(0);
        assertThat(savedItem.getUnitPrice()).isEqualTo(250.0);
        assertThat(savedItem.getMfn()).isEqualTo("NEW-MFN");
    }

    @Test
    @DisplayName("recalculate leaves unit cost untouched when inventory has no offers for the item mfn")
    void recalculateLeavesUnitCostUntouchedWhenInventoryHasNoOffers() {
        // given
        BasketItem item = basketItemWithMfn("MFN-Z", 0.0); // unitCost=0, mfn set → eligible for updateCosts
        Basket basket = basketWith(item);
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));

        // when
        offerItemReloader.recalculate(basket);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        BasketItem savedItem = basketCaptor.getValue().getBasketItems().get(0);
        assertThat(savedItem.getUnitCost()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("reload keeps basket items in position order regardless of category")
    void reloadKeepsBasketItemsInPositionOrderRegardlessOfCategory() {
        // given
        BasketItem laptopItem = new BasketItem("pim-1", "Laptop", "MFN-L",
                ProductCategory.Laptops, 100.0, 0, 1, null, 3, false);
        BasketItem cpuItem = new BasketItem("pim-2", "Processor", "MFN-C",
                ProductCategory.CPU, 50.0, 0, 1, null, 3, false);
        Basket basket = basketWith(laptopItem, cpuItem);

        // when
        offerItemReloader.reload(basket);

        // then
        List<BasketItem> savedItems = savedBasketItems();
        assertThat(savedItems).extracting(BasketItem::getMfn).containsExactly("MFN-L", "MFN-C");
        assertThat(savedItems).extracting(BasketItem::getPosition).containsExactly(0, 1);
    }

    @Test
    @DisplayName("reload sorts basket items by position ascending")
    void reloadSortsBasketItemsByPositionAscending() {
        // given
        BasketItem first = basketItemWithPrice("MFN-1", 100.0);
        BasketItem second = basketItemWithPrice("MFN-2", 100.0);
        BasketItem third = basketItemWithPrice("MFN-3", 100.0);
        Basket basket = basketWith(first, second, third);
        first.setPosition(2);
        second.setPosition(0);
        third.setPosition(1);

        // when
        offerItemReloader.reload(basket);

        // then
        assertThat(savedBasketItems()).extracting(BasketItem::getMfn)
                .containsExactly("MFN-2", "MFN-3", "MFN-1");
    }

    @Test
    @DisplayName("reload puts basket items with null position last")
    void reloadPutsBasketItemsWithNullPositionLast() {
        // given
        BasketItem legacy = basketItemWithPrice("MFN-LEGACY", 100.0);
        BasketItem first = basketItemWithPrice("MFN-1", 100.0);
        BasketItem second = basketItemWithPrice("MFN-2", 100.0);
        Basket basket = basketWith(legacy, first, second);
        legacy.setPosition(null);
        first.setPosition(1);
        second.setPosition(0);

        // when
        offerItemReloader.reload(basket);

        // then
        List<BasketItem> savedItems = savedBasketItems();
        assertThat(savedItems).extracting(BasketItem::getMfn)
                .containsExactly("MFN-2", "MFN-1", "MFN-LEGACY");
        assertThat(savedItems).extracting(BasketItem::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("reload breaks equal positions by unit price descending")
    void reloadBreaksEqualPositionsByUnitPriceDescending() {
        // given
        BasketItem cheap = basketItemWithPrice("MFN-CHEAP", 100.0);
        BasketItem expensive = basketItemWithPrice("MFN-EXPENSIVE", 300.0);
        BasketItem last = basketItemWithPrice("MFN-LAST", 500.0);
        Basket basket = basketWith(cheap, expensive, last);
        cheap.setPosition(0);
        expensive.setPosition(0);
        last.setPosition(1);

        // when
        offerItemReloader.reload(basket);

        // then
        assertThat(savedBasketItems()).extracting(BasketItem::getMfn)
                .containsExactly("MFN-EXPENSIVE", "MFN-CHEAP", "MFN-LAST");
    }

    @Test
    @DisplayName("reload reindexes offer item sequence numbers to match the sorted list")
    void reloadReindexesOfferItemSequenceNumbersToMatchSortedList() {
        // given
        BasketItem first = basketItemWithPrice("MFN-1", 100.0);
        BasketItem second = basketItemWithPrice("MFN-2", 100.0);
        BasketItem third = basketItemWithPrice("MFN-3", 100.0);
        Basket basket = basketWith(first, second, third);
        first.setPosition(2);
        second.setPosition(0);
        third.setPosition(1);

        // when
        List<OfferItem> offerItems = offerItemReloader.reload(basket);

        // then
        assertThat(offerItems).extracting(OfferItem::getSequenceNumber).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("recalculate persists basket exactly once when invoked")
    void recalculatePersistsBasketExactlyOnce() {
        // given
        Basket basket = basketWith(basketItemWithMfn("MFN-1", 50.0));
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));

        // when
        offerItemReloader.recalculate(basket);

        // then
        verify(basketsRepository).save(basket);
    }

    private List<BasketItem> savedBasketItems() {
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        return basketCaptor.getValue().getBasketItems();
    }

    private BasketItem basketItemWithPrice(String mfn, double unitPrice) {
        return new BasketItem("pim-" + mfn, "Product " + mfn, mfn,
                ProductCategory.Laptops, unitPrice, 0, 1, null, 3, false);
    }

    private Basket basketWith(BasketItem... items) {
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.setBasketId(BASKET_ID);
        basket.setBasketItems(new LinkedList<>(List.of(items)));
        return basket;
    }

    private BasketItem basketItemWithCatalog(String pimId, String catalogId, String mfn, double unitPrice) {
        BasketItem item = new BasketItem(pimId, "Test Product", mfn,
                ProductCategory.Laptops, unitPrice, 0, 1, catalogId, 3, false);
        return item;
    }

    private BasketItem basketItemWithMfn(String mfn, double unitCost) {
        BasketItem item = new BasketItem("pim-id", "Other Product", mfn,
                ProductCategory.Laptops, 100.0, unitCost, 1, null, 3, false);
        return item;
    }
}
