package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimView;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryViewStoreListingsTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "5901234567890";
    private static final String MFN = "MFN-1";

    @Mock
    private Warehouse warehouse;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InventoryAutoDiscovery autoDiscovery;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StoreInventoryProvider storeInventoryProvider;
    @Mock
    private GlobalMatchedInventory globalInventory;
    @Mock
    private PimView pimView;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView globalPimView;
    @Mock
    private PimView storePimView;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private Inventory inventory;

    // the global catalog knows PIM-1, so it is a global entry; any other id (st-s-…) is a store entry (spec 4.3)
    @BeforeEach
    void globalCatalogKnowsPim1() {
        when(pimCatalog.withGlobalData()).thenReturn(globalPimView);
        when(pimCatalog.withStoreData(STORE_ID)).thenReturn(storePimView);
        when(globalPimView.findByPimId("PIM-1")).thenReturn(Optional.of(
                new PimEntry("PIM-1", List.of(), "Intel", "i7", "CPU", null, true, null, null)));
    }

    private ProductRecommendationEngine recommendationEngine() throws Exception {
        Constructor<ProductRecommendationEngine> ctor =
                ProductRecommendationEngine.class.getDeclaredConstructor(ProductRepository.class);
        ctor.setAccessible(true);
        return ctor.newInstance(productRepository);
    }

    private CategoryDefinition cpuCategory() {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setCategory("CPU");
        definition.setPimCategoryIds(new LinkedList<>(List.of("989")));
        return definition;
    }

    private InventoryItem item(String supplier, double price) {
        return new InventoryItem(EAN, MFN, price, "PLN", 5, 1, supplier, true, true, false);
    }

    private MatchedInventory globalGroupWithPimId() {
        InventoryKey key = new InventoryKey("PIM-1");
        key.addEan(EAN);
        key.addManufacturerCode(MFN);
        return new MatchedInventory(key, List.of(
                item("AB Group", 1399.0),
                item("Action", 1450.0),
                item("Elko", 1300.0)), taxonomyCache, supplierRegistry);
    }

    private MatchedInventory ownActionGroup() {
        return new MatchedInventory(new InventoryKey(EAN, MFN), List.of(item("Action", 1380.0)), taxonomyCache, supplierRegistry);
    }

    private void storeWithGlobalAbGroupAndOwnAction() {
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of(globalGroupWithPimId()));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, "989"));
    }

    private void stubGlobalIndex(Collection<MatchedInventory> groups) {
        when(globalInventory.index()).thenReturn(InventoryIndex.of(groups));
    }

    private double priceOf(MatchedInventory matched, String supplier) {
        return matched.getInventoryItems().stream()
                .filter(i -> i.supplier().equals(supplier))
                .mapToDouble(InventoryItem::netPrice)
                .findFirst().orElse(-1);
    }

    @Test
    void findAllByProductCategoryIdsReachesStoreOwnInventory() {
        // given
        storeWithGlobalAbGroupAndOwnAction();

        // when
        Collection<MatchedInventory> result =
                inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategoryIds(List.of("989")).get("989");

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
        assertThat(priceOf(matched, "Action")).isEqualTo(1380.0);
    }

    @Test
    void findAllWithPimIdReachesStoreOwnInventory() {
        // given
        storeWithGlobalAbGroupAndOwnAction();

        // when
        Collection<MatchedInventory> result = inventory.withEnabledSuppliersOnly(STORE_ID).findAllWithPimId();

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
        assertThat(priceOf(matched, "Action")).isEqualTo(1380.0);
    }

    @Test
    void getRecommendationsSurfacesOwnInventoryForOwnOnlyStore() throws Exception {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of());
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("Elko", 1300.0), item("Action", 1450.0)), taxonomyCache, supplierRegistry)));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));
        when(productRepository.findAll("cat-1")).thenReturn(List.of());
        when(pimView.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());

        // when
        List<ProductRecommendation> recommendations =
                recommendationEngine().getRecommendations(pimView, cpuCategory(), inventory.withEnabledSuppliersOnly(STORE_ID));

        // then
        assertThat(recommendations).hasSize(1);
        ProductRecommendation recommendation = recommendations.getFirst();
        assertThat(recommendation.getAlternativeSuppliers()).containsExactly("Action");
        assertThat(recommendation.getLowestGrossPrice()).isEqualTo(Price.fromNet(1380.0).grossValue());
    }

    @Test
    void getRecommendationsSurfacesOwnProductMissingFromGlobalBase() throws Exception {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of());
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of());
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));
        when(productRepository.findAll("cat-1")).thenReturn(List.of());
        when(pimView.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());

        // when
        List<ProductRecommendation> recommendations =
                recommendationEngine().getRecommendations(pimView, cpuCategory(), inventory.withEnabledSuppliersOnly(STORE_ID));

        // then
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.getFirst().getAlternativeSuppliers()).containsExactly("Action");
    }

    @Test
    void doesNotDuplicateProductWhenOwnSharesOnlyPimIdWithGlobal() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        InventoryKey ownKey = new InventoryKey("PIM-1");
        MatchedInventory ownGroup = new MatchedInventory(ownKey,
                List.of(new InventoryItem("5907777777777", "MFN-OWN", 1380.0, "PLN", 5, 1, "Action", true, true, false)),
                taxonomyCache, supplierRegistry);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownGroup)));
        InventoryKey globalKey = new InventoryKey("PIM-1");
        globalKey.addEan(EAN);
        globalKey.addManufacturerCode(MFN);
        stubGlobalIndex(List.of(new MatchedInventory(globalKey,
                List.of(item("AB Group", 1399.0)), taxonomyCache, supplierRegistry)));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, "989"));

        // when
        Collection<MatchedInventory> result =
                inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategoryIds(List.of("989")).get("989");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
    }

    @Test
    void findAllByProductCategoryIdsSurfacesOwnOnlyProductThroughFullWarehouseChain() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of());
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, "989"));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        when(stockQueryService.searchAvailableByMfns(any(), any())).thenReturn(List.of());

        // when
        Collection<MatchedInventory> result =
                inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findAllByProductCategoryIds(List.of("989")).get("989");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSuppliers()).containsExactly("Action");
    }

    @Test
    void findByProductCodeSelectsLargestOfDuplicateGlobalGroups() {
        // given
        MatchedInventory smaller = new MatchedInventory(new InventoryKey("5900000000001", MFN),
                List.of(new InventoryItem("5900000000001", MFN, 100.0, "PLN", 5, 1, "SupplierX", true, true, false)),
                taxonomyCache, supplierRegistry);
        MatchedInventory bigger = new MatchedInventory(new InventoryKey("5900000000002", MFN),
                List.of(new InventoryItem("5900000000002", MFN, 90.0, "PLN", 5, 1, "SupplierY", true, true, false),
                        new InventoryItem("5900000000002", MFN, 95.0, "PLN", 5, 1, "SupplierZ", true, true, false)),
                taxonomyCache, supplierRegistry);
        stubGlobalIndex(List.of(smaller, bigger));

        // when
        MatchedInventory result = inventory.withGlobalData().findByProductCode(MFN);

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("SupplierY", "SupplierZ");
    }

    @Test
    void doesNotDuplicateSupplierEnabledAsOwnWhilePresentInGlobalPool() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0), item("Action", 1450.0)), taxonomyCache, supplierRegistry)));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByProductCode(MFN);

        // then
        assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
        assertThat(matched.getInventoryItemsFromSupplier("Action")).hasSize(1);
        assertThat(priceOf(matched, "Action")).isEqualTo(1380.0);
    }

    @Test
    void findByEanPreservesPimIdOfMatchedGlobalGroup() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        InventoryKey globalKey = new InventoryKey("PIM-1");
        globalKey.addEan(EAN);
        globalKey.addManufacturerCode(MFN);
        stubGlobalIndex(List.of(new MatchedInventory(globalKey,
                List.of(item("AB Group", 1399.0)), taxonomyCache, supplierRegistry)));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByEan(EAN);

        // then
        assertThat(matched.getInventoryKey().getId()).isEqualTo("PIM-1");
        assertThat(matched.getInventoryKey().getProductCodes()).contains(MFN);
    }

    @Test
    void findByEanSurfacesWarehouseStockViaMfnResolvedFromGlobal() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0)), taxonomyCache, supplierRegistry)));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = org.mockito.Mockito.mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(new InventoryItem(EAN, MFN, 1200.0, "PLN", 3, 1, "Warehouse", true, true, false));
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), argThat(mfns -> mfns.contains(MFN)))).thenReturn(List.of(view));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findByEan(EAN);

        // then
        assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Warehouse");
    }

    @Test
    void findAllByProductCategoryIdsAppendsWarehouseStock() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0)), taxonomyCache, supplierRegistry)));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, "989"));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = org.mockito.Mockito.mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(new InventoryItem(EAN, MFN, 1200.0, "PLN", 3, 1, "Warehouse", true, true, false));
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), argThat(mfns -> mfns.contains(MFN)))).thenReturn(List.of(view));

        // when
        Collection<MatchedInventory> result =
                inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findAllByProductCategoryIds(List.of("989")).get("989");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSuppliers()).containsExactlyInAnyOrder("AB Group", "Warehouse");
    }

    @Test
    void withWarehouseDataOnlySurfacesWarehouseStock() {
        // given
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = org.mockito.Mockito.mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(new InventoryItem(EAN, MFN, 1200.0, "PLN", 3, 1, "Warehouse", true, true, false));
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), argThat(mfns -> mfns.contains(MFN)))).thenReturn(List.of(view));

        // when
        MatchedInventory matched = inventory.withWarehouseDataOnly(STORE_ID).findByProductCode(MFN);

        // then
        assertThat(matched.getSuppliers()).containsExactly("Warehouse");
    }

    @Test
    void findByProductCodeReturnsEmptyNonNullResultWhenNothingMatches() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of());

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByProductCode("UNKNOWN");

        // then
        assertThat(matched).isNotNull();
        assertThat(matched.isEmpty()).isTrue();
        assertThat(matched.getMfnCodes()).contains("UNKNOWN");
    }

    @Test
    void rowWhoseCategoryNameMatchesButHasNoCategoryIdDoesNotMatch() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of(globalGroupWithPimId()));
        when(taxonomyCache.find(any())).thenReturn(
                new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, null));

        // when
        Map<String, Collection<MatchedInventory>> result =
                inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategoryIds(List.of("Procesory"));

        // then
        assertThat(result.get("Procesory")).isEmpty();
    }

    @Test
    void taxonomyLookupCountIsIndependentOfSelectionSize() {
        // given
        storeWithGlobalAbGroupAndOwnAction();

        // when
        inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategoryIds(List.of("989"));
        Collection<org.mockito.invocation.Invocation> afterSingle =
                org.mockito.Mockito.mockingDetails(taxonomyCache).getInvocations();
        long singleRunFinds = afterSingle.stream().filter(i -> i.getMethod().getName().equals("find")).count();
        org.mockito.Mockito.clearInvocations(taxonomyCache);
        inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategoryIds(List.of("989", "170"));
        long doubleRunFinds = org.mockito.Mockito.mockingDetails(taxonomyCache).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("find")).count();

        // then
        assertThat(doubleRunFinds).isEqualTo(singleRunFinds);
    }

    @Test
    void productReachableThroughTwoSelectedCategoriesAppearsExactlyOnce() {
        // given — pre-merge lookup key carries only MFN-A (categoryId "194", worse score);
        // a second InventorySource merges in MFN-B during assemble() (categoryId "195", better/lower score) —
        // grouping must use the pre-merge taxonomy, landing the product in "194" and never in "195"
        TaxonomyRepository taxonomyRepository = org.mockito.Mockito.mock(TaxonomyRepository.class);
        TaxonomyCache realCache = new TaxonomyCache(taxonomyRepository);
        realCache.add(new Taxonomy(EAN, "MFN-A", "Logitech", "K120", "Klawiatury", 2, null, null, null, "194"));
        realCache.add(new Taxonomy(EAN, "MFN-B", "Logitech", "K120", "Myszki", 1, null, null, null, "195"));

        InventoryKey keyA = new InventoryKey(EAN, "MFN-A");
        MatchedInventory groupA = new MatchedInventory(keyA,
                List.of(new InventoryItem(EAN, "MFN-A", 100.0, "PLN", 5, 1, "AB Group", true, true, false)),
                realCache, supplierRegistry);
        InventoryIndex globalIndex = InventoryIndex.of(List.of(groupA));

        InventoryKey keyB = new InventoryKey(EAN, "MFN-B");
        MatchedInventory groupB = new MatchedInventory(keyB,
                List.of(new InventoryItem(EAN, "MFN-B", 90.0, "PLN", 5, 1, "AB Group", true, true, false)),
                realCache, supplierRegistry);
        InventoryIndex mergingIndex = InventoryIndex.of(List.of(groupB));

        InventoryView view = new InventoryView(globalIndex, InventoryIndex.of(List.of()), pimId -> false,
                realCache, supplierRegistry,
                GroupInventorySource.global(globalIndex, supplier -> true),
                GroupInventorySource.global(mergingIndex, supplier -> true));

        // when
        Map<String, Collection<MatchedInventory>> result = view.findAllByProductCategoryIds(List.of("194", "195"));

        // then
        assertThat(result.get("194").size() + result.get("195").size()).isEqualTo(1);
        assertThat(result.get("194")).hasSize(1);
        assertThat(result.get("195")).isEmpty();
    }

    @Test
    void findByEanSurfacesOwnProductMissingFromGlobalBase() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of());
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of());

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByEan(EAN);

        // then
        assertThat(matched.getSuppliers()).containsExactly("Action");
    }

    private Store storeWithGlobalAbAndOwnIndex(MatchedInventory... ownGroups) {
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        InventoryIndex ownIndex = InventoryIndex.of(List.of(ownGroups));
        when(storeInventoryProvider.ownIndex(store)).thenReturn(ownIndex);
        when(storeInventoryProvider.ownInventory(store)).thenReturn(new StoreInventory(ownIndex, LocalDateTime.now()));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, "989"));
        return store;
    }

    // W-1: the store added entry st-s-1 because the global PIM lacks the product, and a global supplier sells the
    // same EAN; the global group has no PIM id, so it is the same product as the store entry, listed and counted once
    @Test
    void storeEntrySoldAlsoByAGlobalSupplierWithoutGlobalEntryIsListedAndCountedOnce() {
        // given
        InventoryKey ownKey = new InventoryKey("st-s-1");
        MatchedInventory ownGroup = new MatchedInventory(ownKey, List.of(item("Action", 1380.0)), taxonomyCache, supplierRegistry);
        storeWithGlobalAbAndOwnIndex(ownGroup);
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN), List.of(item("AB Group", 1399.0)),
                taxonomyCache, supplierRegistry)));

        // when
        InventoryView view = inventory.withEnabledSuppliersOnly(STORE_ID);
        Collection<MatchedInventory> listed = view.findAllByProductCategoryIds(List.of("989")).get("989");
        Collection<MatchedInventory> withPimId = view.findAllWithPimId();
        InventoryStatistics stats = inventory.storeStatistics(STORE_ID);

        // then
        assertThat(listed).singleElement().satisfies(matched -> {
            assertThat(matched.getInventoryKey().getId()).isEqualTo("st-s-1");
            assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
        });
        assertThat(withPimId).singleElement().extracting(matched -> matched.getInventoryKey().getId()).isEqualTo("st-s-1");
        assertThat(stats.distinctProducts()).isEqualTo(1);
        assertThat(stats.bySupplier()).containsOnlyKeys("AB Group", "Action");
    }

    // c-own (spec 4.3): global entry PIM-1 (…031/MFN-G, and a global feed row …031/MFN-S), store entry st-s-1
    // (…032/MFN-S); they share MFN-S but are different products, so the store entry keeps its own row
    @Test
    void storeEntrySharingAnMpnWithAnotherGlobalEntryKeepsItsOwnRowWithoutTheGlobalOffer() {
        // given
        InventoryKey globalKey = new InventoryKey("PIM-1");
        globalKey.addEan("5900000000031");
        globalKey.addManufacturerCode("MFN-G");
        MatchedInventory globalGroup = new MatchedInventory(globalKey,
                List.of(new InventoryItem("5900000000031", "MFN-S", 1399.0, "PLN", 5, 1, "AB Group", true, true, false)),
                taxonomyCache, supplierRegistry);
        InventoryKey ownKey = new InventoryKey("st-s-1");
        MatchedInventory ownGroup = new MatchedInventory(ownKey,
                List.of(new InventoryItem("5900000000032", "MFN-S", 1380.0, "PLN", 5, 1, "Action", true, true, false)),
                taxonomyCache, supplierRegistry);
        storeWithGlobalAbAndOwnIndex(ownGroup);
        stubGlobalIndex(List.of(globalGroup));

        // when
        InventoryView view = inventory.withEnabledSuppliersOnly(STORE_ID);
        Collection<MatchedInventory> listed = view.findAllByProductCategoryIds(List.of("989")).get("989");
        InventoryStatistics stats = inventory.storeStatistics(STORE_ID);

        // then
        assertThat(listed).extracting(matched -> matched.getInventoryKey().getId()).containsExactly("PIM-1", "st-s-1");
        MatchedInventory storeEntryRow = listed.stream()
                .filter(matched -> "st-s-1".equals(matched.getInventoryKey().getId())).findFirst().orElseThrow();
        assertThat(storeEntryRow.getSuppliers()).containsExactly("Action");
        assertThat(stats.distinctProducts()).isEqualTo(2);
    }

    // a clone (st-s-c) of the global entry PIM-1 keeps the offers of the global suppliers that sell PIM-1
    private InventoryKey cloneOfPim1() {
        when(storePimView.findOwnClones()).thenReturn(List.of(new PimEntry("st-s-c", List.of(), "Intel", "i7",
                "CPU", null, true, null, null, null, STORE_ID, "PIM-1")));
        InventoryKey cloneKey = new InventoryKey("st-s-c");
        cloneKey.addEan(EAN);
        cloneKey.addManufacturerCode(MFN);
        return cloneKey;
    }

    @Test
    void cloneRowIsListedOnceWithGlobalOffers() {
        // given
        InventoryKey cloneKey = cloneOfPim1();
        storeWithGlobalAbAndOwnIndex(new MatchedInventory(cloneKey, List.of(item("Action", 1380.0)), taxonomyCache, supplierRegistry));
        stubGlobalIndex(List.of(globalGroupWithPimId()));

        // when
        InventoryView view = inventory.withEnabledSuppliersOnly(STORE_ID);
        Collection<MatchedInventory> listed = view.findAllByProductCategoryIds(List.of("989")).get("989");
        InventoryStatistics stats = inventory.storeStatistics(STORE_ID);

        // then
        assertThat(listed).singleElement().satisfies(matched ->
                assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action"));
        assertThat(stats.distinctProducts()).isEqualTo(1);
    }

    @Test
    void cloneSourcesAreReadOncePerView() {
        // given
        InventoryKey cloneKey = cloneOfPim1();
        storeWithGlobalAbAndOwnIndex(new MatchedInventory(cloneKey, List.of(item("Action", 1380.0)), taxonomyCache, supplierRegistry));
        stubGlobalIndex(List.of(globalGroupWithPimId()));

        // when: several lookups on one view, each of them resolving the clone's source
        InventoryView view = inventory.withEnabledSuppliersOnly(STORE_ID);
        view.findAllByProductCategoryIds(List.of("989"));
        view.findByInventoryKey(cloneKey);
        view.findByInventoryKey(cloneKey);

        // then
        verify(storePimView, times(1)).findOwnClones();
        verify(storePimView, never()).findByPimId(any());
    }

    // ProductDetails resolves an automatic category product through findByInventoryKey of the store's view
    @Test
    void productDetailsOfACloneInAnAutomaticCategoryShowGlobalOffers() {
        // given
        InventoryKey cloneKey = cloneOfPim1();
        storeWithGlobalAbAndOwnIndex();
        stubGlobalIndex(List.of(globalGroupWithPimId()));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByInventoryKey(cloneKey);

        // then
        assertThat(matched.getSuppliers()).containsExactly("AB Group");
        assertThat(matched.getInventoryKey().getId()).isEqualTo("st-s-c");
    }
}
