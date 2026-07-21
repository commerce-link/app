package pl.commercelink.baskets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
                "Laptops", 250L, 5L, 3, 0L, false);
        Pricelist pricelist = new Pricelist("pricelist-1", List.of(freshPriceData));
        when(pricelistRepository.findNewestPricelistIdCached(STORE_ID, "cat-1")).thenReturn("pricelist-1");
        when(pricelistRepository.find(STORE_ID, "cat-1", "pricelist-1")).thenReturn(pricelist);

        // when
        offerItemReloader.recalculate(basket);

        // then
        assertThat(item.getUnitPrice()).isEqualTo(250.0);
        assertThat(item.getMfn()).isEqualTo("NEW-MFN");
    }

    @Test
    @DisplayName("recalculate leaves unit cost untouched when inventory has no offers for the item mfn")
    void recalculateLeavesUnitCostUntouchedWhenInventoryHasNoOffers() {
        // given
        BasketItem item = basketItemWithMfn("MFN-Z", 0.0); // unitCost=0, mfn set → eligible for updateCosts
        Basket basket = basketWith(item);

        // when
        offerItemReloader.recalculate(basket);

        // then
        assertThat(item.getUnitCost()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("reload returns offer items sorted by position without mutating the basket")
    void reloadReturnsOfferItemsSortedByPositionWithoutMutatingTheBasket() {
        // given
        BasketItem laptopItem = new BasketItem("pim-1", "Laptop", "MFN-L",
                "Laptops", 100.0, 0, 1, null, 3, false);
        BasketItem cpuItem = new BasketItem("pim-2", "Processor", "MFN-C",
                "CPU", 50.0, 0, 1, null, 3, false);
        Basket basket = basketWith(laptopItem, cpuItem);

        // when
        List<OfferItem> offerItems = offerItemReloader.reload(basket);

        // then
        assertThat(offerItems).extracting(o -> o.getBasketItem().getMfn()).containsExactly("MFN-L", "MFN-C");
        assertThat(offerItems).extracting(OfferItem::getSequenceNumber).containsExactly(0, 1);
        assertThat(basket.getBasketItems()).extracting(BasketItem::getMfn).containsExactly("MFN-L", "MFN-C");
    }

    @Test
    @DisplayName("reload sorts products before services following the position bands")
    void reloadSortsProductsBeforeServices() {
        // given
        BasketItem service = BasketItem.shipping("Dostawa", 20.0);
        BasketItem product = new BasketItem("pim-1", "Laptop", "MFN-L",
                "Laptops", 100.0, 0, 1, null, 3, false);
        Basket basket = basketWith(service, product);

        // when
        List<OfferItem> offerItems = offerItemReloader.reload(basket);

        // then
        assertThat(offerItems).extracting(o -> o.getBasketItem().getMfn()).containsExactly("MFN-L", "SHIPPING");
        assertThat(offerItems.get(0).getBasketItem().getPosition())
                .isLessThan(offerItems.get(1).getBasketItem().getPosition());
    }

    @Test
    @DisplayName("sequence numbers keep pointing at the basket list index so form fields bind the displayed item")
    void sequenceNumbersPointAtBasketListIndexEvenWhenDisplayOrderDiffers() {
        // given - a mid-list item became a service, so list order differs from position order
        BasketItem service = new BasketItem("pim-s", "Montaż", "MFN-S",
                "Usługi dodatkowe", 250.0, 0, 1, null, 3, false);
        service.setService(true);
        service.setPosition(800);
        BasketItem product = new BasketItem("pim-1", "Obudowa Corsair", "MFN-C",
                "Case", 399.0, 0, 1, null, 3, false);
        product.setPosition(0);
        Basket basket = new Basket();
        basket.setStoreId(STORE_ID);
        basket.getBasketItems().addAll(List.of(service, product));

        // when
        List<OfferItem> offerItems = offerItemReloader.reload(basket);

        // then
        assertThat(offerItems).extracting(o -> o.getBasketItem().getMfn()).containsExactly("MFN-C", "MFN-S");
        for (OfferItem offerItem : offerItems) {
            assertThat(basket.getBasketItems().get(offerItem.getSequenceNumber()))
                    .isSameAs(offerItem.getBasketItem());
        }
    }

    @Test
    @DisplayName("reload breaks equal-position ties by unit price descending")
    void reloadBreaksEqualPositionTiesByUnitPriceDescending() {
        // given
        BasketItem cheaper = new BasketItem("pim-1", "Cheaper", "MFN-CHEAP",
                "Laptops", 100.0, 0, 1, null, 3, false);
        BasketItem pricier = new BasketItem("pim-2", "Pricier", "MFN-PRICEY",
                "Laptops", 500.0, 0, 1, null, 3, false);
        Basket basket = basketWith(cheaper, pricier);
        basket.getBasketItems().forEach(item -> item.setPosition(7));

        // when
        List<OfferItem> offerItems = offerItemReloader.reload(basket);

        // then
        assertThat(offerItems).extracting(o -> o.getBasketItem().getMfn())
                .containsExactly("MFN-PRICEY", "MFN-CHEAP");
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
                "Laptops", unitPrice, 0, 1, catalogId, 3, false);
        return item;
    }

    private BasketItem basketItemWithMfn(String mfn, double unitCost) {
        BasketItem item = new BasketItem("pim-id", "Other Product", mfn,
                "Laptops", 100.0, unitCost, 1, null, 3, false);
        return item;
    }
}
