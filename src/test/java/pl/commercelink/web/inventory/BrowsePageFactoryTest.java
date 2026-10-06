package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.inventory.BrowseCriteria;
import pl.commercelink.inventory.BrowseResult;
import pl.commercelink.inventory.BrowseRow;
import pl.commercelink.inventory.BrowseSummary;
import pl.commercelink.inventory.InventoryBrowse;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Integration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrowsePageFactoryTest {

    private static final String STORE_ID = "store-1";

    @Mock private InventoryBrowse inventoryBrowse;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private StoresRepository storesRepository;
    @Mock private Warehouse warehouse;
    @Mock private StockQueryService stock;
    private BrowsePageFactory factory;

    @BeforeEach
    void setUp() {
        PimCatalog pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"),
                new PimCategory("11", "10", "Karty graficzne", "pl"),
                new PimCategory("12", "10", "Dyski SSD", "pl"),
                new PimCategory("20", null, "Akcesoria", "pl"),
                new PimCategory("21", "20", "Kable", "pl")));
        when(storesRepository.findById(STORE_ID)).thenReturn(store(
                labelled("Kosatec-k7f3a9c2", "Kosatec B2B"),
                labelled("manual-a1b2c3d4", "Hurtownia Nowak"),
                labelled("Elko-a1b2c3d4", "Elektronika"),
                labelled("Action-a1b2c3d4", "Elektronika")));
        factory = new BrowsePageFactory(inventoryBrowse, new PimCategoryTree(pimCatalog), catalogPlacement,
                new SupplierLabels(storesRepository), new WarehouseStockLookup(warehouse, storesRepository));
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stock);
        when(inventoryBrowse.summary(any())).thenReturn(new BrowseSummary(Map.of("11", 3, "12", 2, "21", 9), Map.of("AB", 14), 14));
        when(inventoryBrowse.browse(any(), any())).thenReturn(new BrowseResult(List.of(), 0, false));
        CatalogPlacement.Target gpu = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"));
        CatalogPlacement.Target b2b = new CatalogPlacement.Target("c-2", "Sklep B2B", "cat-b2b", "Karty", List.of("11"));
        CatalogPlacement.Existing existing = new CatalogPlacement.Existing("c-1", "cat-gpu", "p-1", InventoryKey.fromEan("5901000000001"));
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(gpu, b2b), List.of(existing)));
    }

    @Test
    void startShowsTopLevelTilesByCountAndQueriesNoRows() {
        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        assertThat(page.isStart()).isTrue();
        assertThat(page.tiles()).extracting(BrowsePage.Tile::label).containsExactly("Akcesoria", "Komponenty komputerowe");
        assertThat(page.tiles()).extracting(BrowsePage.Tile::count).containsExactly(9, 5);
        assertThat(page.tiles().get(1).description()).isEqualTo("Karty graficzne, Dyski SSD");
        verify(inventoryBrowse, never()).browse(any(), any());
    }

    @Test
    void categoryShowsCrumbsChildrenAndQueriesItsWholeSubtree() {
        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("10"), true, false);

        // then
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().categoryIds()).containsExactlyInAnyOrder("10", "11", "12");
        assertThat(page.title()).isEqualTo("Komponenty komputerowe");
        assertThat(page.crumbs()).extracting(BrowsePage.Crumb::label).containsExactly(null, "Komponenty komputerowe");
        assertThat(page.subnavSiblings()).isFalse();
        assertThat(page.subnav()).extracting(BrowsePage.NavItem::label).containsExactly("Dyski SSD", "Karty graficzne");
    }

    @Test
    void leafShowsSiblingsWithTheCurrentOneMarked() {
        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), true, false);

        // then
        assertThat(page.subnavSiblings()).isTrue();
        assertThat(page.subnav()).filteredOn(BrowsePage.NavItem::current).extracting(BrowsePage.NavItem::label)
                .containsExactly("Karty graficzne");
    }

    @Test
    void rowShowsPimPathCatalogLineAndInCatalogLinkForAdmin() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000001", "11", "Karty graficzne")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("10"), true, false);

        // then
        BrowsePage.RowView row = page.rows().get(0);
        assertThat(row.category().pimAncestors()).containsExactly("Komponenty komputerowe");
        assertThat(row.category().pimLeaf()).isEqualTo("Karty graficzne");
        assertThat(row.category().catalogLabel()).isEqualTo("Podzespoły › Karta graficzna");
        assertThat(row.category().catalogMore()).isEqualTo(1);
        assertThat(row.category().inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-gpu/products/p-1");
        assertThat(row.detailHref()).startsWith("/dashboard/inventory/prices?q=5901000000001&from=%2Fdashboard%2Finventory%3Fcat%3D10");
        assertThat(row.addHref()).isEqualTo("/dashboard/inventory?cat=10&open=add&ean=5901000000001");
    }

    @Test
    void rowWithUnknownCategoryIdFallsBackToCategoryText() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000002", "777", "Zasilacze awaryjne")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("777"), true, false);

        // then
        assertThat(page.rows().get(0).category().pimAncestors()).isEmpty();
        assertThat(page.rows().get(0).category().pimLeaf()).isEqualTo("Zasilacze awaryjne");
        assertThat(page.rows().get(0).category().catalogUnmatched()).isTrue();
    }

    @Test
    void nonAdminGetsNoCatalogLineAndNoPlacementLookup() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000001", "11", "Karty graficzne")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), false, false);

        // then
        assertThat(page.rows().get(0).category().catalogLabel()).isNull();
        assertThat(page.rows().get(0).category().catalogUnmatched()).isFalse();
        verifyNoInteractions(catalogPlacement);
    }

    @Test
    void catalogFilterOutKeepsOnlyProductsOutsideTheCatalogs() {
        // when
        factory.build(STORE_ID, BrowseQuery.start().withCategory("11").withCatalog(BrowseQuery.CatalogFilter.OUT), true, false);

        // then
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().rowFilter().test(row("5901000000001", "11", "x"))).isFalse();
        assertThat(criteria.getValue().rowFilter().test(row("5909999999999", "11", "x"))).isTrue();
    }

    /**
     * A store's own offer joins the global product it matches by any one code, and when it is the cheapest the row shows
     * and adds it by that offer's EAN. A product added by those codes is in the catalog: the row and the catalog filter
     * must say so, although the group itself is keyed by the other supplier's codes.
     */
    @Test
    void productAddedByTheCodesTheRowShowsCountsAsInTheCatalog() {
        // given
        BrowseRow merged = new BrowseRow(InventoryKey.fromEan("5909999999998"), "Produkt", "Brand", "5901000000001",
                "MFN-OWN", "11", "Karty graficzne", 120.0, true, "AB", 5, 2);
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(merged), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11").withCatalog(BrowseQuery.CatalogFilter.OUT), true, false);

        // then
        assertThat(page.rows().get(0).category().inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-gpu/products/p-1");
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().rowFilter().test(merged)).isFalse();
    }

    @Test
    void pageIsClampedToLastPage() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any()))
                .thenReturn(new BrowseResult(List.of(row("5901000000001", "11", "x")), 120, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11").withPage(9), true, false);

        // then
        assertThat(page.pagination().page()).isEqualTo(3);
        assertThat(page.rows()).hasSize(1);
        verify(inventoryBrowse, times(1)).browse(any(), any());
    }

    @Test
    void supplierMenuChipsAndCostShowConnectionLabelsWhileFilteringByIdentity() {
        // given
        when(inventoryBrowse.summary(STORE_ID)).thenReturn(new BrowseSummary(Map.of("11", 5),
                Map.of("Kosatec-k7f3a9c2", 3, "manual-a1b2c3d4", 2), 5));
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000001", "11", "Karty graficzne", "Kosatec-k7f3a9c2")), 1, false));
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("cat", "11");
        params.add("supplier", "Kosatec-k7f3a9c2");

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.parse(params), true, false);

        // then
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().suppliers()).containsExactly("Kosatec-k7f3a9c2");
        assertThat(page.supplierOptions()).extracting(BrowsePage.MenuOption::value)
                .containsExactly("manual-a1b2c3d4", "Kosatec-k7f3a9c2");
        assertThat(page.supplierOptions()).extracting(BrowsePage.MenuOption::label)
                .containsExactly("Hurtownia Nowak", "Kosatec B2B");
        assertThat(page.chips()).extracting(BrowsePage.Chip::value).containsExactly("Kosatec B2B");
        assertThat(page.rows().get(0).costSupplier()).isEqualTo("Kosatec B2B");
    }

    @Test
    void supplierMenuTellsApartConnectionsSharingALabel() {
        // given
        when(inventoryBrowse.summary(STORE_ID)).thenReturn(new BrowseSummary(Map.of("11", 5),
                Map.of("Elko-a1b2c3d4", 3, "Action-a1b2c3d4", 2), 5));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        assertThat(page.supplierOptions()).extracting(BrowsePage.MenuOption::label)
                .containsExactly("Elektronika (Action-a1b2c3d4)", "Elektronika (Elko-a1b2c3d4)");
    }

    @Test
    void superAdminSeesGlobalSupplierNamesAsTheyAre() {
        // given
        when(inventoryBrowse.summary(null)).thenReturn(new BrowseSummary(Map.of("11", 5), Map.of("AB", 5), 5));

        // when
        BrowsePage page = factory.build(null, BrowseQuery.start(), true, true);

        // then
        assertThat(page.supplierOptions()).extracting(BrowsePage.MenuOption::label).containsExactly("AB");
        verifyNoInteractions(storesRepository);
    }

    @Test
    void unknownPimCategoryIdsCountAndBrowseAsUnassigned() {
        // given
        when(inventoryBrowse.summary(STORE_ID)).thenReturn(new BrowseSummary(Map.of("11", 3, "-", 2, "777", 4),
                Map.of("AB", 9), 9));

        // when
        BrowsePage start = factory.build(STORE_ID, BrowseQuery.start(), true, false);
        factory.build(STORE_ID, BrowseQuery.start().withCategory("-"), true, false);

        // then
        assertThat(start.tiles()).filteredOn(tile -> "inventory.browse.unassigned".equals(tile.labelKey()))
                .extracting(BrowsePage.Tile::count).containsExactly(6);
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().categoryIds()).containsExactlyInAnyOrder("-", "777");
    }

    @Test
    void onlyUnknownPimCategoryIdsStillShowTheUnassignedTile() {
        // given
        when(inventoryBrowse.summary(STORE_ID)).thenReturn(new BrowseSummary(Map.of("11", 3, "777", 4),
                Map.of("AB", 7), 7));

        // when
        BrowsePage start = factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        assertThat(start.tiles()).extracting(BrowsePage.Tile::labelKey).contains("inventory.browse.unassigned");
        assertThat(start.tiles()).filteredOn(tile -> "inventory.browse.unassigned".equals(tile.labelKey()))
                .extracting(BrowsePage.Tile::count).containsExactly(4);
    }

    @Test
    void pageWithoutSuppliersFlagsNoSuppliers() {
        // given
        when(inventoryBrowse.summary(STORE_ID)).thenReturn(BrowseSummary.EMPTY);

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        assertThat(page.noSuppliers()).isTrue();
    }

    @Test
    void textTooShortSkipsTheQuery() {
        // given
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q2", "ab");

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.parse(params), true, false);

        // then
        assertThat(page.textTooShort()).isTrue();
        verify(inventoryBrowse, never()).browse(anyString(), any());
    }

    @Test
    void rowsShowTheStoresInStockQuantityFromOneWarehouseCallForThePage() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                mfnRow("MFN-A"), mfnRow("MFN-B"), mfnRow("MFN-C")), 3, false));
        when(stock.searchAllAvailableByMfns(eq(STORE_ID), anyCollection())).thenReturn(List.of(
                warehouseItem("mfn-a", 4, FulfilmentStatus.Delivered),
                warehouseItem("MFN-A", 2, FulfilmentStatus.Delivered),
                warehouseItem("MFN-A", 7, FulfilmentStatus.Ordered),
                warehouseItem("MFN-B", 3, FulfilmentStatus.Ordered)));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), true, false);

        // then
        assertThat(page.rows()).extracting(BrowsePage.RowView::warehouseQty).containsExactly(6L, 0L, 0L);
        assertThat(page.rows()).extracting(BrowsePage.RowView::qty).containsExactly(5L, 5L, 5L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.forClass(Collection.class);
        verify(stock, times(1)).searchAllAvailableByMfns(eq(STORE_ID), codes.capture());
        assertThat(codes.getValue()).containsExactlyInAnyOrder("MFN-A", "MFN-B", "MFN-C");
    }

    @Test
    void pageWithMoreCodesThanOneWarehouseQueryTakesSplitsThemIntoBatches() {
        // given
        List<BrowseRow> rows = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            rows.add(mfnRow("MFN-" + i));
        }
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(rows, 101, false));
        when(stock.searchAllAvailableByMfns(eq(STORE_ID), anyCollection())).thenReturn(List.of())
                .thenReturn(List.of(warehouseItem("MFN-100", 2, FulfilmentStatus.Delivered)));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), true, false);

        // then
        verify(stock, times(2)).searchAllAvailableByMfns(eq(STORE_ID), anyCollection());
        assertThat(page.rows().get(100).warehouseQty()).isEqualTo(2L);
    }

    @Test
    void superAdminGetsNoWarehouseLine() {
        // given
        when(inventoryBrowse.browse(any(), any())).thenReturn(new BrowseResult(List.of(mfnRow("MFN-A")), 1, false));

        // when
        BrowsePage page = factory.build(null, BrowseQuery.start().withCategory("11"), true, true);

        // then
        assertThat(page.rows()).extracting(BrowsePage.RowView::warehouseQty).containsExactly(0L);
        verifyNoInteractions(warehouse);
    }

    @Test
    void storeWithAnExternalWmsGetsNoWarehouseLine() {
        // given
        Store store = store();
        store.setIntegrations(new ArrayList<>(List.of(new Integration(IntegrationType.WMS_PROVIDER, "wms"))));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(mfnRow("MFN-A")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), true, false);

        // then
        assertThat(page.rows()).extracting(BrowsePage.RowView::warehouseQty).containsExactly(0L);
        verifyNoInteractions(warehouse);
    }

    @Test
    void failingWarehouseLeavesThePageWithoutTheLine() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(mfnRow("MFN-A")), 1, false));
        when(stock.searchAllAvailableByMfns(eq(STORE_ID), anyCollection())).thenThrow(new IllegalStateException("scan failed"));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), true, false);

        // then
        assertThat(page.rows()).extracting(BrowsePage.RowView::warehouseQty).containsExactly(0L);
    }

    @Test
    void pageWithoutRowsAsksTheWarehouseNothing() {
        // when
        factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        verifyNoInteractions(stock);
    }

    private static BrowseRow mfnRow(String mfn) {
        return new BrowseRow(InventoryKey.fromMfn(mfn), "Produkt " + mfn, "Brand", null, mfn, "11", "Karty graficzne",
                120.0, true, "AB", 5, 2);
    }

    private static WarehouseItemView warehouseItem(String mfn, int qty, FulfilmentStatus status) {
        return new WarehouseItemView(STORE_ID, "item-" + mfn + qty, "Produkt", "5900000000011", mfn, Price.fromNet(100.0),
                qty, status, ItemCondition.Sealed);
    }

    private static BrowseRow row(String ean, String categoryId, String categoryText) {
        return row(ean, categoryId, categoryText, "AB");
    }

    private static BrowseRow row(String ean, String categoryId, String categoryText, String supplier) {
        return new BrowseRow(InventoryKey.fromEan(ean), "Produkt " + ean, "Brand", ean, "MFN-" + ean, categoryId,
                categoryText, 120.0, true, supplier, 5, 2);
    }

    private static Store store(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private static StoreSupplierConnection labelled(String identity, String label) {
        StoreSupplierConnection connection = new StoreSupplierConnection(identity, ConnectionMode.OWN);
        connection.setLabel(label);
        return connection;
    }
}
