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
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(inventoryView);
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
        when(pricelistRepository.findNewestPricelistIdCached("cat-1")).thenReturn("pricelist-1");
        when(pricelistRepository.find("cat-1", "pricelist-1")).thenReturn(pricelist);
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));

        // when
        offerItemReloader.recalculate(STORE_ID, basket);

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
        offerItemReloader.recalculate(STORE_ID, basket);

        // then
        ArgumentCaptor<Basket> basketCaptor = ArgumentCaptor.forClass(Basket.class);
        verify(basketsRepository).save(basketCaptor.capture());
        BasketItem savedItem = basketCaptor.getValue().getBasketItems().get(0);
        assertThat(savedItem.getUnitCost()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("recalculate persists basket exactly once when invoked")
    void recalculatePersistsBasketExactlyOnce() {
        // given
        Basket basket = basketWith(basketItemWithMfn("MFN-1", 50.0));
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));

        // when
        offerItemReloader.recalculate(STORE_ID, basket);

        // then
        verify(basketsRepository).save(basket);
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
