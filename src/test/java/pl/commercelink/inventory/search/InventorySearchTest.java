package pl.commercelink.inventory.search;

import org.junit.jupiter.api.BeforeEach;
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
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventorySearchTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "5901234123457";
    private static final String MFN = "910-006559";

    @Mock
    private Inventory inventory;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private Warehouse warehouse;

    @InjectMocks
    private InventorySearch search;

    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private InventoryView view;
    @Mock
    private StockQueryService stock;
    private Store store;

    @BeforeEach
    void setUp() {
        SupplierInfo supplierInfo = mock(SupplierInfo.class);
        when(supplierInfo.shippingTermsFor("PL")).thenReturn(new ShippingTerms(2, null));
        when(supplierRegistry.get(anyString())).thenReturn(supplierInfo);
        store = mock(Store.class);
        StoreSupplierConnection elko = mock(StoreSupplierConnection.class);
        when(elko.getSupplierName()).thenReturn("Elko");
        when(elko.getMode()).thenReturn(ConnectionMode.GLOBAL);
        when(store.getSupplierConnections()).thenReturn(List.of(elko));
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(inventory.withEnabledSuppliersOnly(STORE_ID)).thenReturn(view);
        when(inventory.withGlobalData()).thenReturn(view);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stock);
        when(view.findByEan(anyString())).thenReturn(empty());
        when(view.findByProductCode(anyString())).thenReturn(empty());
        when(view.findByInventoryKey(any())).thenReturn(empty());
        when(pimCatalog.findByPimId(anyString())).thenReturn(Optional.empty());
        when(pimCatalog.findByGtin(anyString())).thenReturn(Optional.empty());
        when(stock.searchAllAvailableByMfns(eq(STORE_ID), anyCollection())).thenReturn(List.of());
    }

    private MatchedInventory empty() {
        return new MatchedInventory(new InventoryKey(), taxonomyCache, supplierRegistry);
    }

    private MatchedInventory offers(InventoryItem... items) {
        return new MatchedInventory(new InventoryKey(EAN, MFN), List.of(items), taxonomyCache, supplierRegistry);
    }

    private InventoryItem offer(String supplier, double netPrice, int qty) {
        return new InventoryItem(EAN, MFN, netPrice, "PLN", qty, 1, supplier);
    }

    private WarehouseItemView warehouseItem(double netCost, int qty, FulfilmentStatus status, ItemCondition condition) {
        return new WarehouseItemView(STORE_ID, "item-" + qty, EAN, MFN, Price.fromNet(netCost), qty, status, condition);
    }

    @Test
    void matchesByEanBeforeOtherIdentifiers() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));
        when(view.findByProductCode(EAN)).thenReturn(offers(offer("AB", 90.0, 5)));

        // when
        InventorySearchResult result = search.search(STORE_ID, EAN);

        // then
        assertThat(result).isInstanceOf(InventorySearchResult.Found.class);
        InventorySearchResult.Found found = (InventorySearchResult.Found) result;
        assertThat(found.matchedBy()).isEqualTo(MatchedBy.EAN);
        assertThat(found.supplierOffers()).extracting(OfferRow::supplier).containsExactly("Elko");
        assertThat(found.supplierOffers().get(0).mode()).isEqualTo(ConnectionMode.GLOBAL);
    }

    @Test
    void fallsBackToManufacturerCode() {
        // given
        when(view.findByProductCode(MFN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult result = search.search(STORE_ID, MFN);

        // then
        assertThat(((InventorySearchResult.Found) result).matchedBy()).isEqualTo(MatchedBy.MFN);
    }

    @Test
    void fallsBackToPimId() {
        // given
        PimEntry entry = mock(PimEntry.class);
        when(entry.pimId()).thenReturn("PIM-7");
        when(entry.gtins()).thenReturn(List.of(EAN));
        when(entry.mpns()).thenReturn(List.of(MFN));
        when(pimCatalog.findByPimId("PIM-7")).thenReturn(Optional.of(entry));
        when(view.findByInventoryKey(any())).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult result = search.search(STORE_ID, "PIM-7");

        // then
        assertThat(((InventorySearchResult.Found) result).matchedBy()).isEqualTo(MatchedBy.PIM_ID);
    }

    @Test
    void queriesTheWarehouseExactlyOnceWithTheMatchedManufacturerCodes() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        search.search(STORE_ID, EAN);

        // then
        verify(stock, times(1)).searchAllAvailableByMfns(STORE_ID, new InventoryKey(EAN, MFN).getProductCodes());
        verify(stock, times(1)).searchAllAvailableByMfns(anyString(), anyCollection());
    }

    @Test
    void notFoundStillQueriesTheWarehouseOnlyOnce() {
        // when
        InventorySearchResult result = search.search(STORE_ID, "ABC-123-XX");

        // then
        assertThat(result).isEqualTo(new InventorySearchResult.NotFound("ABC-123-XX"));
        verify(stock, times(1)).searchAllAvailableByMfns(anyString(), anyCollection());
    }

    @Test
    void productOnlyInTheWarehouseIsFoundByManufacturerCode() {
        // given
        when(stock.searchAllAvailableByMfns(eq(STORE_ID), anyCollection()))
                .thenReturn(List.of(warehouseItem(80.0, 3, FulfilmentStatus.Delivered, ItemCondition.Sealed)));

        // when
        InventorySearchResult result = search.search(STORE_ID, MFN);

        // then
        InventorySearchResult.Found found = (InventorySearchResult.Found) result;
        assertThat(found.matchedBy()).isEqualTo(MatchedBy.MFN);
        assertThat(found.supplierOffers()).isEmpty();
        assertThat(found.prices().warehouseInStockQty()).isEqualTo(3);
        assertThat(found.prices().hasSupplierOffers()).isFalse();
    }

    @Test
    void productKnownFromTaxonomyWithoutOffersIsReportedAsKnown() {
        // given
        when(taxonomyCache.findByMfn(anyString())).thenReturn(new Taxonomy(EAN, MFN, "Logitech", "MX Keys S", "Keyboards", 1, null, null, null, "1"));

        // when
        InventorySearchResult result = search.search(STORE_ID, MFN);

        // then
        assertThat(result).isInstanceOf(InventorySearchResult.KnownWithoutOffers.class);
        assertThat(((InventorySearchResult.KnownWithoutOffers) result).product().name()).isEqualTo("MX Keys S");
    }

    @Test
    void cheapestIgnoresWarehouseCostAndZeroPrices() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("AB", 110.0, 80), offer("Nowak", 0.0, 0), offer("Elko", 100.0, 58)));
        when(stock.searchAllAvailableByMfns(eq(STORE_ID), anyCollection())).thenReturn(List.of(
                warehouseItem(50.0, 2, FulfilmentStatus.Ordered, ItemCondition.Damaged),
                warehouseItem(50.0, 3, FulfilmentStatus.Delivered, ItemCondition.Sealed)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::supplier).containsExactly("Elko", "AB", "Nowak");
        assertThat(found.supplierOffers()).extracting(OfferRow::cheapest).containsExactly(true, false, false);
        assertThat(found.prices().lowestGross()).isEqualTo(Price.fromNet(100.0).grossValue());
        assertThat(found.supplierOffers().get(0).grossPrice()).isEqualTo(Price.fromNet(100.0).grossValue());
        assertThat(found.supplierOffers().get(0).deliveryDays()).isEqualTo(3);
        assertThat(found.warehouseRows().get(0).grossUnitCost()).isEqualTo(Price.fromNet(50.0).grossValue());
        assertThat(found.prices().lowestSupplierLabel()).isEqualTo("Elko");
        assertThat(found.prices().suppliersWithStock()).isEqualTo(2);
        assertThat(found.prices().supplierCount()).isEqualTo(3);
        assertThat(found.warehouseRows()).extracting(WarehouseRow::inDelivery).containsExactly(false, true);
        assertThat(found.warehouseRows().get(1).hasSpecialCondition()).isTrue();
        assertThat(found.prices().warehouseInStockQty()).isEqualTo(3);
        assertThat(found.prices().warehouseInDeliveryQty()).isEqualTo(2);
    }

    @Test
    void globalSearchNeverTouchesTheWarehouse() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult result = search.searchGlobal(EAN);

        // then
        assertThat(((InventorySearchResult.Found) result).supplierOffers().get(0).mode()).isEqualTo(ConnectionMode.GLOBAL);
        verify(warehouse, never()).stockQueryService(anyString());
    }

    @Test
    void storeWithExternalWarehouseSkipsTheBuiltInWarehouse() {
        // given
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        search.search(STORE_ID, EAN);

        // then
        verify(warehouse, never()).stockQueryService(anyString());
    }

    @Test
    void lowestPriceMatchesTheCheapestRowEvenForASingleUnitOffer() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 1), offer("AB", 110.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.prices().lowestGross()).isEqualTo(Price.fromNet(100.0).grossValue());
        assertThat(found.supplierOffers()).filteredOn(OfferRow::cheapest).extracting(OfferRow::supplier).containsExactly("Elko");
    }

    @Test
    void offersWithoutPricesDoNotBreakTheSummary() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 0.0, 5), offer("AB", 0.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.prices().lowestGross()).isEqualTo(0.0);
        assertThat(found.prices().medianGross()).isEqualTo(0.0);
        assertThat(found.supplierOffers()).extracting(OfferRow::cheapest).containsExactly(false, false);
    }

    @Test
    void medianIsTheMeanOfTheTwoMiddlePricesForAnEvenCount() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("A", 100.0, 1), offer("B", 110.0, 1), offer("C", 120.0, 1), offer("D", 130.0, 1)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.prices().medianGross())
                .isCloseTo((Price.fromNet(110.0).grossValue() + Price.fromNet(120.0).grossValue()) / 2, within(0.001));
    }

    @Test
    void tiedCheapestOffersAreAllMarkedCheapest() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("Elko", 100.0, 5), offer("AB", 100.0, 5), offer("Kosatec", 110.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::supplier).containsExactly("Elko", "AB", "Kosatec");
        assertThat(found.supplierOffers()).extracting(OfferRow::cheapest).containsExactly(true, true, false);
    }

    @Test
    void productKnownOnlyToPimByGtinIsReportedAsKnown() {
        // given
        PimEntry entry = mock(PimEntry.class);
        when(entry.name()).thenReturn("Logitech MX Keys S");
        when(entry.brand()).thenReturn("Logitech");
        when(entry.gtins()).thenReturn(List.of(EAN));
        when(entry.mpns()).thenReturn(List.of(MFN));
        when(pimCatalog.findByGtin(EAN)).thenReturn(Optional.of(entry));

        // when
        InventorySearchResult result = search.search(STORE_ID, EAN);

        // then
        assertThat(result).isInstanceOf(InventorySearchResult.KnownWithoutOffers.class);
        InventorySearchResult.KnownWithoutOffers known = (InventorySearchResult.KnownWithoutOffers) result;
        assertThat(known.matchedBy()).isEqualTo(MatchedBy.EAN);
        assertThat(known.product().name()).isEqualTo("Logitech MX Keys S");
    }

    @Test
    void productKnownOnlyToPimByIdIsReportedAsKnown() {
        // given
        PimEntry entry = mock(PimEntry.class);
        when(entry.name()).thenReturn("Logitech MX Keys S");
        when(entry.gtins()).thenReturn(List.of(EAN));
        when(entry.mpns()).thenReturn(List.of(MFN));
        when(pimCatalog.findByPimId("PIM-7")).thenReturn(Optional.of(entry));

        // when
        InventorySearchResult result = search.search(STORE_ID, "PIM-7");

        // then
        assertThat(result).isInstanceOf(InventorySearchResult.KnownWithoutOffers.class);
        assertThat(((InventorySearchResult.KnownWithoutOffers) result).matchedBy()).isEqualTo(MatchedBy.PIM_ID);
    }

    @Test
    void storeSearchMarksTheWarehouseAsChecked() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.warehouseChecked()).isTrue();
    }

    @Test
    void globalSearchNeverMarksTheWarehouseAsChecked() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.searchGlobal(EAN);

        // then
        assertThat(found.warehouseChecked()).isFalse();
    }

    @Test
    void storeWithExternalWarehouseMarksTheWarehouseAsNotChecked() {
        // given
        when(store.hasIntegration(IntegrationType.WMS_PROVIDER)).thenReturn(true);
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.warehouseChecked()).isFalse();
    }
}
