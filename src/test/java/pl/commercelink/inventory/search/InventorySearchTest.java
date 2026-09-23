package pl.commercelink.inventory.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
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
        // the label map is real: resolving a connection's label is part of what a search returns
        search = new InventorySearch(inventory, storesRepository, pimCatalog, taxonomyCache, warehouse,
                new SupplierLabels(storesRepository), supplierRegistry);
        // shipping is free and instant unless a test says otherwise, so a case about prices stays about prices
        when(supplierRegistry.exists(anyString())).thenReturn(true);
        when(supplierRegistry.get(anyString())).thenReturn(supplierShipping(new ShippingCostPolicy.Free(), 0));
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

    private static SupplierInfo supplierShipping(ShippingCostPolicy policy, int arrivalDays) {
        return new SupplierInfo("Any", SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(arrivalDays, policy)));
    }

    private MatchedInventory empty() {
        return new MatchedInventory(new InventoryKey(), supplierRegistry);
    }

    private MatchedInventory offers(InventoryItem... items) {
        return new MatchedInventory(new InventoryKey(EAN, MFN), List.of(items), supplierRegistry);
    }

    private InventoryItem offer(String supplier, double netPrice, int qty) {
        return offer(supplier, EAN, MFN, netPrice, qty);
    }

    private InventoryItem offer(String supplier, String ean, String mfn, double netPrice, int qty) {
        return new InventoryItem(ean, mfn, netPrice, "PLN", qty, 1, supplier);
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
    void offerCarriesTheConnectionLabelAndFallsBackToTheIdentityWithoutOne() {
        // given -- one connection named by the operator, one left with the default name
        StoreSupplierConnection own = mock(StoreSupplierConnection.class);
        when(own.getSupplierName()).thenReturn("Kosatec-k7f3a9c2");
        when(own.getMode()).thenReturn(ConnectionMode.OWN);
        when(own.getLabel()).thenReturn("Kosatec Wrocław");
        StoreSupplierConnection elko = mock(StoreSupplierConnection.class);
        when(elko.getSupplierName()).thenReturn("Elko");
        when(elko.getMode()).thenReturn(ConnectionMode.GLOBAL);
        when(store.getSupplierConnections()).thenReturn(List.of(own, elko));
        when(view.findByEan(EAN)).thenReturn(offers(offer("Kosatec-k7f3a9c2", 100.0, 5), offer("Elko", 110.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::supplierLabel)
                .containsExactly("Kosatec Wrocław", "Elko");
    }

    /** Shipping comes from the supplier plugin, the same terms the fulfilment planner quotes. */
    @Test
    void offerCarriesTheSuppliersDeliveryCostFreeShippingThresholdAndTotalLeadTime() {
        // given -- 18 zl below a 1000 zl basket, two days in transit on top of the supplier's own day
        when(supplierRegistry.get("Elko"))
                .thenReturn(supplierShipping(new ShippingCostPolicy.FlatRate(1000, 18), 2));
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        OfferRow row = found.supplierOffers().get(0);
        assertThat(row.deliveryNet()).isEqualTo(18.0);
        assertThat(row.freeDeliveryFrom()).isEqualTo(1000.0);
        assertThat(row.leadTimeDays()).isEqualTo(3);
        assertThat(row.totalNet()).isEqualTo(118.0);
        assertThat(row.deliveryKnown()).isTrue();
    }

    /**
     * Half of the real adapters say "never free" with a million-zloty threshold, because `FlatRate` has no
     * other way to express it. Shown literally that becomes "free above 1 000 000 PLN" on those suppliers.
     */
    @Test
    void anUnreachableFreeShippingThresholdIsReportedAsNoThresholdAtAll() {
        // given
        when(supplierRegistry.get("Elko"))
                .thenReturn(supplierShipping(new ShippingCostPolicy.FlatRate(1_000_000, 18.90), 1));
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then -- the cost is real and still shown, only the unreachable promise is dropped
        OfferRow row = found.supplierOffers().get(0);
        assertThat(row.deliveryNet()).isEqualTo(18.90);
        assertThat(row.freeDeliveryFrom()).isEqualTo(0.0);
        assertThat(row.hasFreeDeliveryFrom()).isFalse();
    }

    /** A cheaper unit price loses to a dearer one that ships for nothing; that is the whole point of the column. */
    @Test
    void cheapestOfferIsTheOneWithTheLowestDeliveredCostNotTheLowestPrice() {
        // given
        StoreSupplierConnection ab = mock(StoreSupplierConnection.class);
        when(ab.getSupplierName()).thenReturn("AB");
        when(ab.getMode()).thenReturn(ConnectionMode.GLOBAL);
        when(store.getSupplierConnections()).thenReturn(List.of(ab));
        when(supplierRegistry.get("Elko")).thenReturn(supplierShipping(new ShippingCostPolicy.FlatRate(5000, 30), 1));
        when(supplierRegistry.get("AB")).thenReturn(supplierShipping(new ShippingCostPolicy.Free(), 1));
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 100.0, 5), offer("AB", 110.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then -- AB leads the list and wins the marker at 110, Elko trails at 130 delivered
        assertThat(found.supplierOffers()).extracting(OfferRow::supplier).containsExactly("AB", "Elko");
        assertThat(found.supplierOffers().get(0).cheapest()).isTrue();
        assertThat(found.supplierOffers().get(1).cheapest()).isFalse();
        assertThat(found.prices().lowestNet()).isEqualTo(110.0);
        assertThat(found.prices().lowestDeliveredNet()).isEqualTo(110.0);
    }

    /**
     * The registry hands out a placeholder policy for suppliers it does not know. Showing that as a cost
     * would put an invented number in front of someone deciding where to buy.
     */
    @Test
    void supplierWithoutKnownTermsReportsNoShippingRatherThanThePlaceholder() {
        // given
        when(supplierRegistry.exists("Ghost")).thenReturn(false);
        when(view.findByEan(EAN)).thenReturn(offers(offer("Ghost", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        OfferRow row = found.supplierOffers().get(0);
        assertThat(row.deliveryKnown()).isFalse();
        assertThat(row.hasLeadTime()).isFalse();
        assertThat(row.totalNet()).isEqualTo(100.0);
    }

    /** Two connections of one supplier render the same name, so the rows say which codes they matched on. */
    @Test
    void offersRenderingUnderTheSameNameAreFlaggedAsAmbiguous() {
        // given -- neither connection was given a label, so both fall back to the same identity shape
        StoreSupplierConnection first = mock(StoreSupplierConnection.class);
        when(first.getSupplierName()).thenReturn("AcmeB");
        when(first.getMode()).thenReturn(ConnectionMode.OWN);
        StoreSupplierConnection second = mock(StoreSupplierConnection.class);
        when(second.getSupplierName()).thenReturn("AcmeB-k7f3a9c2");
        when(second.getMode()).thenReturn(ConnectionMode.OWN);
        when(second.getLabel()).thenReturn("AcmeB");
        when(store.getSupplierConnections()).thenReturn(List.of(first, second));
        when(view.findByEan(EAN)).thenReturn(offers(offer("AcmeB", 100.0, 5), offer("AcmeB-k7f3a9c2", 120.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::sharesLabel).containsExactly(true, true);
    }

    /** A doubtful match can win on price, so the summary has to say the headline figure is doubtful. */
    @Test
    void summaryFlagsTheCheapestOfferWhenItWasMatchedOnDifferentCodes() {
        // given -- the cheapest row carries neither the searched EAN nor its code
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("Elko", "5903000000002", "OTHER-CODE", 100.0, 5),
                offer("Elko", EAN, MFN, 200.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers().get(0).codeMatch()).isEqualTo(CodeMatch.BOTH_DIFFER);
        assertThat(found.prices().lowestIsUncertainMatch()).isTrue();
    }

    @Test
    void globalSearchLabelsOffersByTheirIdentityBecauseItSpansStores() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Kosatec-k7f3a9c2", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.searchGlobal(EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::supplierLabel).containsExactly("Kosatec-k7f3a9c2");
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
        assertThat(found.prices().hasLowestPrice()).isFalse();
        assertThat(found.product().mfn()).isEqualTo(MFN);
        assertThat(found.warehouseRows()).extracting(WarehouseRow::codeMatch).containsExactly(CodeMatch.SAME);
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
        assertThat(found.prices().lowestNet()).isEqualTo(100.0);
        assertThat(found.supplierOffers().get(0).grossPrice()).isEqualTo(Price.fromNet(100.0).grossValue());
        assertThat(found.warehouseRows().get(0).grossUnitCost()).isEqualTo(Price.fromNet(50.0).grossValue());
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
        assertThat(found.prices().lowestNet()).isEqualTo(100.0);
        assertThat(found.supplierOffers()).filteredOn(OfferRow::cheapest).extracting(OfferRow::supplier).containsExactly("Elko");
    }

    @Test
    void offersWithoutPricesDoNotBreakTheSummary() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(offer("Elko", 0.0, 5), offer("AB", 0.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.prices().lowestNet()).isEqualTo(0.0);
        assertThat(found.prices().medianNet()).isEqualTo(0.0);
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
        assertThat(found.prices().medianNet()).isCloseTo((110.0 + 120.0) / 2, within(0.001));
        assertThat(found.prices().showsMedian()).isTrue();
    }

    @Test
    void medianIsHiddenBelowFourPricedOffersInStock() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("A", 100.0, 1), offer("B", 110.0, 1), offer("C", 120.0, 1), offer("D", 90.0, 0)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.prices().pricedOffersInStock()).isEqualTo(3);
        assertThat(found.prices().showsMedian()).isFalse();
    }

    @Test
    void cheapestOfferIsChosenOnlyAmongOffersInStockAndOffersWithoutStockCloseTheList() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("Elko", 90.0, 0), offer("Kosatec", 110.0, 3), offer("AB", 100.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::supplier).containsExactly("AB", "Kosatec", "Elko");
        assertThat(found.supplierOffers()).extracting(OfferRow::cheapest).containsExactly(true, false, false);
        assertThat(found.prices().lowestNet()).isEqualTo(100.0);
        assertThat(found.prices().supplierQty()).isEqualTo(8);
    }

    @Test
    void flagsOffersWhoseCodesDifferFromTheSearchedProduct() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("Elko", "05901234123457", MFN, 100.0, 5),
                offer("AB", "5903000000000", MFN, 101.0, 5),
                offer("Kosatec", EAN, "910-OTHER", 102.0, 5),
                offer("Nowak", "5900000000099", "MXM3S-BOX", 103.0, 5),
                offer("Action", null, " 910-006559 ", 104.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.supplierOffers()).extracting(OfferRow::codeMatch).containsExactly(
                CodeMatch.SAME, CodeMatch.EAN_DIFFERS, CodeMatch.CODE_DIFFERS, CodeMatch.BOTH_DIFFER, CodeMatch.SAME);
        assertThat(found.supplierOffers()).extracting(offer -> offer.codeMatch().isInfo()).containsExactly(false, true, true, false, false);
        assertThat(found.supplierOffers().get(3).codeMatch().isWarning()).isTrue();
    }

    @Test
    void productHeaderShowsTheSearchedEanAndTheCodeMostOffersCarry() {
        // given
        when(view.findByEan(EAN)).thenReturn(offers(
                offer("Elko", EAN, "ALT-1", 100.0, 5),
                offer("AB", EAN, MFN, 101.0, 5),
                offer("Kosatec", EAN, MFN, 102.0, 5)));

        // when
        InventorySearchResult.Found found = (InventorySearchResult.Found) search.search(STORE_ID, EAN);

        // then
        assertThat(found.product().ean()).isEqualTo(EAN);
        assertThat(found.product().mfn()).isEqualTo(MFN);
        assertThat(found.supplierOffers()).extracting(OfferRow::codeMatch)
                .containsExactly(CodeMatch.CODE_DIFFERS, CodeMatch.SAME, CodeMatch.SAME);
    }

    @Test
    void productHeaderCodesDoNotDependOnOfferOrderWhenCountsTie() {
        // given
        when(view.findByProductCode(MFN)).thenReturn(offers(offer("Elko", "5900000000002", MFN, 100.0, 5), offer("AB", "5900000000001", MFN, 101.0, 5)));

        // when
        InventorySearchResult.Found first = (InventorySearchResult.Found) search.search(STORE_ID, MFN);
        when(view.findByProductCode(MFN)).thenReturn(offers(offer("AB", "5900000000001", MFN, 101.0, 5), offer("Elko", "5900000000002", MFN, 100.0, 5)));
        InventorySearchResult.Found second = (InventorySearchResult.Found) search.search(STORE_ID, MFN);

        // then
        assertThat(first.product().ean()).isEqualTo("5900000000001").isEqualTo(second.product().ean());
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
