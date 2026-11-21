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
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductDataFixture;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.ItemCondition;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private ProductDataResolver productDataResolver;

    @InjectMocks
    private Inventory inventory;

    @BeforeEach
    void productDataComesFromTheTaxonomy() {
        ProductDataFixture.answerFromTaxonomy(productDataResolver, taxonomyCache);
    }

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
                item("Elko", 1300.0)), supplierRegistry);
    }

    private MatchedInventory ownActionGroup() {
        return new MatchedInventory(new InventoryKey(EAN, MFN), List.of(item("Action", 1380.0)), supplierRegistry);
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
                List.of(item("Elko", 1300.0), item("Action", 1450.0)), supplierRegistry)));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "CPU", 1, null, null, null, "989"));
        when(pimView.findByPimIdOrGtins(any(), any())).thenReturn(Optional.empty());

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
        when(pimView.findByPimIdOrGtins(any(), any())).thenReturn(Optional.empty());

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
                supplierRegistry);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownGroup)));
        InventoryKey globalKey = new InventoryKey("PIM-1");
        globalKey.addEan(EAN);
        globalKey.addManufacturerCode(MFN);
        stubGlobalIndex(List.of(new MatchedInventory(globalKey,
                List.of(item("AB Group", 1399.0)), supplierRegistry)));
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

        // when
        Collection<MatchedInventory> result =
                inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findAllByProductCategoryIds(List.of("989")).get("989");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSuppliers()).containsExactly("Action");
    }

    @Test
    void findByEanSelectsLargestOfGlobalGroupsSharingTheEan() {
        // given
        MatchedInventory smaller = new MatchedInventory(new InventoryKey(EAN, "MFN-A"),
                List.of(new InventoryItem(EAN, "MFN-A", 100.0, "PLN", 5, 1, "SupplierX", true, true, false)),
                supplierRegistry);
        MatchedInventory bigger = new MatchedInventory(new InventoryKey(EAN, "MFN-B"),
                List.of(new InventoryItem(EAN, "MFN-B", 90.0, "PLN", 5, 1, "SupplierY", true, true, false),
                        new InventoryItem(EAN, "MFN-B", 95.0, "PLN", 5, 1, "SupplierZ", true, true, false)),
                supplierRegistry);
        stubGlobalIndex(List.of(smaller, bigger));

        // when
        MatchedInventory result = inventory.withGlobalData().findByEan(EAN);

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
                List.of(item("AB Group", 1399.0), item("Action", 1450.0)), supplierRegistry)));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByEan(EAN);

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
                List.of(item("AB Group", 1399.0)), supplierRegistry)));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByEan(EAN);

        // then
        assertThat(matched.getInventoryKey().getId()).isEqualTo("PIM-1");
        assertThat(matched.getInventoryKey().getProductCodes()).contains(MFN);
    }

    @Test
    void findByEanSurfacesWarehouseStock() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0)), supplierRegistry)));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = new WarehouseItemView(STORE_ID, "wh-1", "Widget", EAN, MFN, Price.fromNet(1200.0, 0.23), 3,
                FulfilmentStatus.Delivered, ItemCondition.Sealed);
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(view));

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
                List.of(item("AB Group", 1399.0)), supplierRegistry)));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", "Procesory", 1, null, null, null, "989"));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = new WarehouseItemView(STORE_ID, "wh-1", "Widget", EAN, MFN, Price.fromNet(1200.0, 0.23), 3,
                FulfilmentStatus.Delivered, ItemCondition.Sealed);
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(view));

        // when
        Collection<MatchedInventory> result =
                inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findAllByProductCategoryIds(List.of("989")).get("989");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSuppliers()).containsExactlyInAnyOrder("AB Group", "Warehouse");
    }

    @Test
    void itemOfAWarehouseOnlyProductIsFoundByEan() {
        // given
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = new WarehouseItemView(STORE_ID, "wh-1", "Widget", EAN, MFN, Price.fromNet(1200.0, 0.23), 3,
                FulfilmentStatus.Delivered, ItemCondition.Sealed);
        when(stockQueryService.searchAllAvailable(STORE_ID)).thenReturn(List.of(view));

        // when
        MatchedInventory matched = inventory.withWarehouseDataOnly(STORE_ID).findByEan(EAN);

        // then
        assertThat(matched.getSuppliers()).containsExactly("Warehouse");
    }

    @Test
    void findByEanReturnsEmptyNonNullResultWhenNothingMatches() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of());

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersOnly(STORE_ID).findByEan("5900000000999");

        // then
        assertThat(matched).isNotNull();
        assertThat(matched.isEmpty()).isTrue();
        assertThat(matched.getEans()).contains("5900000000999");
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
    void productDataReadCountIsIndependentOfSelectionSize() {
        // given
        storeWithGlobalAbGroupAndOwnAction();

        // when
        Collection<MatchedInventory> oneCategory = inventory.withEnabledSuppliersOnly(STORE_ID)
                .findAllByProductCategoryIds(List.of("989")).get("989");
        long oneCategoryReads = productDataReads();
        org.mockito.Mockito.clearInvocations(productDataResolver);
        Collection<MatchedInventory> twoCategories = inventory.withEnabledSuppliersOnly(STORE_ID)
                .findAllByProductCategoryIds(List.of("989", "170")).get("989");
        long twoCategoriesReads = productDataReads();

        // then
        assertThat(oneCategory).hasSize(1);
        assertThat(twoCategories).hasSize(1);
        assertThat(oneCategoryReads).isEqualTo(2);
        assertThat(twoCategoriesReads).isEqualTo(2);
    }

    private long productDataReads() {
        return org.mockito.Mockito.mockingDetails(productDataResolver).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("ofGroups"))
                .count();
    }

    @Test
    void productReachableThroughTwoSelectedCategoriesAppearsExactlyOnce() {
        // given — pre-merge lookup key carries only EAN (categoryId "194", worse score);
        // a second InventorySource merges in a second EAN of the same code during assemble() (categoryId "195",
        // better/lower score) — grouping must use the pre-merge taxonomy, landing the product in "194" and never in "195"
        String secondEan = "5901234567891";
        TaxonomyRepository taxonomyRepository = org.mockito.Mockito.mock(TaxonomyRepository.class);
        TaxonomyCache realCache = new TaxonomyCache(taxonomyRepository);
        realCache.add(new Taxonomy(EAN, "MFN-A", "Logitech", "K120", "Klawiatury", 2, null, null, null, "194"));
        realCache.add(new Taxonomy(secondEan, "MFN-A", "Logitech", "K120", "Myszki", 1, null, null, null, "195"));

        InventoryKey keyA = new InventoryKey(EAN, "MFN-A");
        MatchedInventory groupA = new MatchedInventory(keyA,
                List.of(new InventoryItem(EAN, "MFN-A", 100.0, "PLN", 5, 1, "AB Group", true, true, false)),
                supplierRegistry);
        InventoryIndex globalIndex = InventoryIndex.of(List.of(groupA));

        InventoryKey keyB = new InventoryKey(secondEan, "MFN-A");
        MatchedInventory groupB = new MatchedInventory(keyB,
                List.of(new InventoryItem(secondEan, "MFN-A", 90.0, "PLN", 5, 1, "AB Group", true, true, false)),
                supplierRegistry);
        InventoryIndex mergingIndex = InventoryIndex.of(List.of(groupB));

        InventoryView view = new InventoryView(null, ProductDataFixture.resolverOver(realCache), globalIndex, InventoryIndex.of(List.of()),
                pimId -> false, supplierRegistry,
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
        MatchedInventory ownGroup = new MatchedInventory(ownKey, List.of(item("Action", 1380.0)), supplierRegistry);
        storeWithGlobalAbAndOwnIndex(ownGroup);
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN), List.of(item("AB Group", 1399.0)),
                supplierRegistry)));

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
                supplierRegistry);
        InventoryKey ownKey = new InventoryKey("st-s-1");
        MatchedInventory ownGroup = new MatchedInventory(ownKey,
                List.of(new InventoryItem("5900000000032", "MFN-S", 1380.0, "PLN", 5, 1, "Action", true, true, false)),
                supplierRegistry);
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
        storeWithGlobalAbAndOwnIndex(new MatchedInventory(cloneKey, List.of(item("Action", 1380.0)), supplierRegistry));
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
        storeWithGlobalAbAndOwnIndex(new MatchedInventory(cloneKey, List.of(item("Action", 1380.0)), supplierRegistry));
        stubGlobalIndex(List.of(globalGroupWithPimId()));

        // when: several lookups on one view, each of them resolving the clone's source (read once, when the view is built)
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

    // Philips: the global group (MxSolution) and the store's own group (Action, Elko) carry different EANs of the same
    // product; the own group is linked to the global one by hand, as the link computation would do
    private static final String PHILIPS_GLOBAL_EAN = "8720689010825";
    private static final String PHILIPS_OWN_EAN = "8720689021180";

    private InventoryItem philips(String ean, String supplier, double price) {
        return new InventoryItem("Philips", null, ean, "QP1324/20", supplier, price, "PLN", 5, 1, true, true, false);
    }

    private MatchedInventory philipsGlobal() {
        return new MatchedInventory(new InventoryKey(PHILIPS_GLOBAL_EAN, "QP1324/20"),
                List.of(philips(PHILIPS_GLOBAL_EAN, "MxSolution", 107.11)), supplierRegistry);
    }

    private MatchedInventory philipsOwn() {
        return new MatchedInventory(new InventoryKey(PHILIPS_OWN_EAN, "QP1324/20"),
                List.of(philips(PHILIPS_OWN_EAN, "Action", 75.00), philips(PHILIPS_OWN_EAN, "Elko", 65.76)),
                supplierRegistry);
    }

    private InventoryView philipsView(java.util.function.Predicate<String> enabledGlobal, InventorySource... extra) {
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        InventoryIndex globalIndex = InventoryIndex.of(List.of(global));
        InventoryIndex ownIndex = InventoryIndex.of(List.of(own));
        ownIndex.link(own, global.getInventoryKey().getProductEans());
        when(taxonomyCache.find(any())).thenReturn(
                new Taxonomy(PHILIPS_GLOBAL_EAN, "QP1324/20", "Philips", "QP1324/20", "Golarki", 1, null, null, null, "31"));
        List<InventorySource> sources = new java.util.ArrayList<>(List.of(
                GroupInventorySource.global(globalIndex, enabledGlobal), GroupInventorySource.own(ownIndex)));
        sources.addAll(List.of(extra));
        return new InventoryView(STORE_ID, productDataResolver, globalIndex, ownIndex, pimId -> false, supplierRegistry,
                sources.toArray(InventorySource[]::new));
    }

    private static List<String> offers(MatchedInventory matched) {
        return matched.getInventoryItems().stream().map(item -> item.supplier() + " " + item.netPrice()).sorted().toList();
    }

    private static final class SpySource implements InventorySource {
        private int calls;

        @Override
        public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
            calls++;
        }
    }

    @Test
    void listedRowOwnEanAndGlobalEanGiveTheSameOffers() {
        // given
        InventoryView view = philipsView(supplier -> true);

        // when
        Collection<MatchedInventory> listed = view.findAllByProductCategoryIds(List.of("31")).get("31");
        MatchedInventory byOwnEan = view.findByEan(PHILIPS_OWN_EAN);
        MatchedInventory byGlobalEan = view.findByEan(PHILIPS_GLOBAL_EAN);

        // then
        List<String> expected = List.of("Action 75.0", "Elko 65.76", "MxSolution 107.11");
        assertThat(listed).singleElement().satisfies(row -> assertThat(offers(row)).isEqualTo(expected));
        assertThat(offers(byOwnEan)).isEqualTo(expected);
        assertThat(offers(byGlobalEan)).isEqualTo(expected);
    }

    @Test
    void linkedRowWithItsGlobalSupplierDisabledShowsOwnOffersOnce() {
        // given
        InventoryView view = philipsView(supplier -> false);

        // when
        Collection<MatchedInventory> listed = view.findAllByProductCategoryIds(List.of("31")).get("31");
        MatchedInventory byOwnEan = view.findByEan(PHILIPS_OWN_EAN);

        // then
        List<String> expected = List.of("Action 75.0", "Elko 65.76");
        assertThat(listed).singleElement().satisfies(row -> assertThat(offers(row)).isEqualTo(expected));
        assertThat(offers(byOwnEan)).isEqualTo(expected);
    }

    @Test
    void lookupWithoutLinksRunsSourcesOnce() {
        // given
        InventoryIndex globalIndex = InventoryIndex.of(List.of(philipsGlobal()));
        InventoryIndex ownIndex = InventoryIndex.of(List.of(philipsOwn()));
        SpySource spy = new SpySource();
        InventoryView view = new InventoryView(STORE_ID, productDataResolver, globalIndex, ownIndex, pimId -> false, supplierRegistry,
                GroupInventorySource.global(globalIndex, supplier -> true), GroupInventorySource.own(ownIndex), spy);

        // when
        MatchedInventory byOwnEan = view.findByEan(PHILIPS_OWN_EAN);

        // then
        assertThat(spy.calls).isEqualTo(1);
        assertThat(offers(byOwnEan)).containsExactly("Action 75.0", "Elko 65.76");
    }

    @Test
    void lookupThatLearnsALinkRunsEverySourceTwice() {
        // given
        SpySource spy = new SpySource();
        InventoryView view = philipsView(supplier -> true, spy);

        // when
        MatchedInventory byOwnEan = view.findByEan(PHILIPS_OWN_EAN);

        // then: the own source ran twice as well, and merging the same group again added no offer
        assertThat(spy.calls).isEqualTo(2);
        assertThat(offers(byOwnEan)).containsExactly("Action 75.0", "Elko 65.76", "MxSolution 107.11");
    }

    // an own group carrying a global PIM id the global pool lacks (the store PIM view matched it by gtin), next to the
    // Philips global group without an id; a global id, not a store entry (the view's StoreEntryKeys knows none)
    private InventoryView philipsViewWithOwnPimId(boolean linked) {
        MatchedInventory global = philipsGlobal();
        InventoryKey ownKey = new InventoryKey("PIM-X");
        ownKey.addEan(PHILIPS_OWN_EAN);
        ownKey.addManufacturerCode("QP1324/20");
        MatchedInventory own = new MatchedInventory(ownKey,
                List.of(philips(PHILIPS_OWN_EAN, "Action", 75.00), philips(PHILIPS_OWN_EAN, "Elko", 65.76)),
                supplierRegistry);
        InventoryIndex globalIndex = InventoryIndex.of(List.of(global));
        InventoryIndex ownIndex = InventoryIndex.of(List.of(own));
        if (linked) {
            ownIndex.link(own, global.getInventoryKey().getProductEans());
        }
        return new InventoryView(STORE_ID, productDataResolver, globalIndex, ownIndex, pimId -> false, supplierRegistry,
                GroupInventorySource.global(globalIndex, supplier -> true), GroupInventorySource.own(ownIndex));
    }

    @Test
    void linkedOwnGroupWithAGlobalPimIdKeepsTheProductInFindAllWithPimId() {
        // given
        InventoryView view = philipsViewWithOwnPimId(true);

        // when
        Collection<MatchedInventory> withPimId = view.findAllWithPimId();

        // then: the own group is covered by the link, so the global row carries its id and the offers of both groups
        assertThat(withPimId).singleElement().satisfies(row -> {
            assertThat(row.getInventoryKey().getId()).isEqualTo("PIM-X");
            assertThat(offers(row)).containsExactly("Action 75.0", "Elko 65.76", "MxSolution 107.11");
        });
    }

    @Test
    void unlinkedOwnGroupWithAGlobalPimIdIsListedAloneInFindAllWithPimId() {
        // given
        InventoryView view = philipsViewWithOwnPimId(false);

        // when
        Collection<MatchedInventory> withPimId = view.findAllWithPimId();

        // then: as before the links, the global group without an id is left out and the own group is its own row
        assertThat(withPimId).singleElement().satisfies(row -> {
            assertThat(row.getInventoryKey().getId()).isEqualTo("PIM-X");
            assertThat(offers(row)).containsExactly("Action 75.0", "Elko 65.76");
        });
    }

    // Known limitation (spec, "Znane ograniczenia"), pinned rather than endorsed: links are computed when the own index is
    // built. After a global reload in which the linked global group also took the EAN of another own group, the row
    // shows that other group (an EAN match outranks a link), while the linked own group stays covered by its link
    // and is shown nowhere until the next build of the own index recomputes the links.
    @Test
    void staleLinkLeavesTheOwnGroupCoveredButNotShownUntilTheNextBuild() {
        // given
        String otherOwnEan = "8720689021197";
        MatchedInventory reloadedGlobal = new MatchedInventory(new InventoryKey(Set.of(PHILIPS_GLOBAL_EAN, otherOwnEan),
                Set.of("QP1324/20")), List.of(philips(PHILIPS_GLOBAL_EAN, "MxSolution", 107.11),
                philips(otherOwnEan, "Wave", 104.00)), supplierRegistry);
        MatchedInventory linkedOwn = philipsOwn();
        MatchedInventory otherOwn = new MatchedInventory(new InventoryKey(otherOwnEan, "QP1324/20"),
                List.of(philips(otherOwnEan, "Komputronik", 99.00)), supplierRegistry);
        InventoryIndex globalIndex = InventoryIndex.of(List.of(reloadedGlobal));
        InventoryIndex ownIndex = InventoryIndex.of(List.of(linkedOwn, otherOwn));
        ownIndex.link(linkedOwn, List.of(PHILIPS_GLOBAL_EAN));
        when(taxonomyCache.find(any())).thenReturn(
                new Taxonomy(PHILIPS_GLOBAL_EAN, "QP1324/20", "Philips", "QP1324/20", "Golarki", 1, null, null, null, "31"));
        InventoryView view = new InventoryView(STORE_ID, productDataResolver, globalIndex, ownIndex, pimId -> false, supplierRegistry,
                GroupInventorySource.global(globalIndex, supplier -> true), GroupInventorySource.own(ownIndex));

        // when
        Collection<MatchedInventory> listed = view.findAllByProductCategoryIds(List.of("31")).get("31");

        // then
        assertThat(listed).singleElement().satisfies(row ->
                assertThat(offers(row)).containsExactly("Komputronik 99.0", "MxSolution 107.11", "Wave 104.0"));
    }

    @Test
    void warehouseStockUnderEitherEanOfALinkedProductIsFoundByBothEans() {
        // given
        StockQueryService stock = org.mockito.Mockito.mock(StockQueryService.class);
        when(stock.searchAllAvailable(STORE_ID)).thenReturn(List.of(
                new WarehouseItemView(STORE_ID, "item-own", "Philips", PHILIPS_OWN_EAN, "QP1324/20",
                        Price.fromNet(60.0, 0.23), 1, FulfilmentStatus.Delivered, ItemCondition.Sealed),
                new WarehouseItemView(STORE_ID, "item-global", "Philips", PHILIPS_GLOBAL_EAN, "QP1324/20",
                        Price.fromNet(61.0, 0.23), 2, FulfilmentStatus.Delivered, ItemCondition.Sealed)));
        InventoryView view = philipsView(supplier -> true, new WarehouseInventorySource(STORE_ID, stock));

        // when
        MatchedInventory byOwnEan = view.findByEan(PHILIPS_OWN_EAN);
        MatchedInventory byGlobalEan = view.findByEan(PHILIPS_GLOBAL_EAN);

        // then
        List<String> expected = List.of("Action 75.0", "Elko 65.76", "MxSolution 107.11",
                "Warehouse 60.0", "Warehouse 61.0");
        assertThat(offers(byOwnEan)).isEqualTo(expected);
        assertThat(offers(byGlobalEan)).isEqualTo(expected);
    }
}
