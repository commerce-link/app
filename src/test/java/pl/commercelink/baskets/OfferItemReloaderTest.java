package pl.commercelink.baskets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.stores.SupplierScope;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
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

    @Spy
    private StorePimAliases aliases = new StorePimAliases(ScopedPimCatalog.of(
            ScopedPimCatalog.globalEntry("pim-source", "EAN-1", "MFN-1", "cat"),
            ScopedPimCatalog.cloneEntry("pim-clone", STORE_ID, "pim-source", "EAN-1", "MFN-1", "cat")));

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
    void basketReloadFindsTheMovedProduct() {
        // given: the basket holds the source id, the newest pricelist is keyed by the store's clone
        BasketItem item = basketItemWithCatalog("pim-source", "cat-1", "OLD-MFN", 100.0);
        AvailabilityAndPrice fresh = new AvailabilityAndPrice(
                "pim-clone", "EAN-1", "NEW-MFN", "Brand", "Label", "Test Product", "Laptops", 250L, 5L, 3, 0L, false);
        when(pricelistRepository.findNewestPricelistIdCached(STORE_ID, "cat-1")).thenReturn("pricelist-1");
        when(pricelistRepository.find(STORE_ID, "cat-1", "pricelist-1")).thenReturn(new Pricelist("pricelist-1", List.of(fresh)));

        // when
        offerItemReloader.recalculate(basketWith(item));

        // then: the price is updated, not left stale
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
    void recalculateNeverWritesACostWhenSuppliersOfferTheItem() {
        // given
        BasketItem noCost = basketItemWithMfn("MFN-A", 0.0);
        BasketItem costed = basketItemWithMfn("MFN-B", 412.30);
        MatchedInventory offered = offeredAt(100, 200, 2);
        when(inventoryView.findByProductCode(any())).thenReturn(offered);
        Basket basket = basketWith(noCost, costed);

        // when
        offerItemReloader.recalculate(basket);

        // then — client 2026-10-27: a cost is what the operator entered; the offer page estimates the rest
        assertThat(noCost.getUnitCost()).isZero();
        assertThat(costed.getUnitCost()).isEqualTo(412.30);
        assertThat(noCost.getEstimatedDeliveryDays()).isEqualTo(2);
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
    @DisplayName("reload keeps services where the operator placed them in the list instead of pushing them to the end")
    void reloadKeepsServicesInListOrder() {
        // given
        BasketItem service = BasketItem.shipping("Dostawa", 20.0);
        BasketItem product = new BasketItem("pim-1", "Laptop", "MFN-L",
                "Laptops", 100.0, 0, 1, null, 3, false);
        Basket basket = basketWith(service, product);

        // when
        List<OfferItem> offerItems = offerItemReloader.reload(basket);

        // then
        assertThat(offerItems).extracting(o -> o.getBasketItem().getMfn()).containsExactly("SHIPPING", "MFN-L");
        assertThat(offerItems).extracting(OfferItem::getSequenceNumber).containsExactly(0, 1);
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
        assertThat(offerItems).extracting(o -> o.getBasketItem().getMfn()).containsExactly("MFN-S", "MFN-C");
        for (OfferItem offerItem : offerItems) {
            assertThat(basket.getBasketItems().get(offerItem.getSequenceNumber()))
                    .isSameAs(offerItem.getBasketItem());
        }
    }

    private static MatchedInventory offeredAt(double lowestNet, double medianNet, int deliveryDays) {
        // deep stubs: building the OfferItem reads more of the matched inventory than this test is about
        MatchedInventory matched = mock(MatchedInventory.class, RETURNS_DEEP_STUBS);
        // the fulfilment candidates come from the supplier offers, so the reloader reaches the matched inventory
        lenient().when(matched.getInventoryItems()).thenReturn(List.of(
                new InventoryItem("5900000000014", "MFN-A", lowestNet, "PLN", 5, deliveryDays, "Acme", true, true, false)));
        when(matched.hasAnyOffers()).thenReturn(true);
        lenient().when(matched.getLowestPrice()).thenReturn(Price.fromNet(lowestNet));
        lenient().when(matched.getMedianPrice()).thenReturn(Price.fromNet(medianNet));
        when(matched.getEstimatedDeliveryDays()).thenReturn(deliveryDays);
        lenient().when(matched.getSuppliers()).thenReturn(List.of("Acme"));
        lenient().when(matched.getMedianPriceSuppliers()).thenReturn(List.of("Acme"));
        return matched;
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
