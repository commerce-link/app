package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private PimCatalog pimCatalog;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private Inventory inventory;

    private ProductRecommendationEngine recommendationEngine() throws Exception {
        Constructor<ProductRecommendationEngine> ctor =
                ProductRecommendationEngine.class.getDeclaredConstructor(PimCatalog.class, ProductRepository.class);
        ctor.setAccessible(true);
        return ctor.newInstance(pimCatalog, productRepository);
    }

    private CategoryDefinition cpuCategory() {
        CategoryDefinition definition = new CategoryDefinition();
        definition.setCategoryId("cat-1");
        definition.setCategory(ProductCategory.CPU);
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
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", ProductCategory.CPU, 1));
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
    void findAllByProductCategoryReachesStoreOwnInventory() {
        // given
        storeWithGlobalAbGroupAndOwnAction();

        // when
        Collection<MatchedInventory> result = inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategory(ProductCategory.CPU.name());

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
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", ProductCategory.CPU, 1));
        when(productRepository.findAll("cat-1")).thenReturn(List.of());
        when(pimCatalog.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());

        // when
        List<ProductRecommendation> recommendations =
                recommendationEngine().getRecommendations(cpuCategory(), inventory.withEnabledSuppliersOnly(STORE_ID));

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
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", ProductCategory.CPU, 1));
        when(productRepository.findAll("cat-1")).thenReturn(List.of());
        when(pimCatalog.findByPimIdOrGtinsOrMpns(any(), any(), any())).thenReturn(Optional.empty());

        // when
        List<ProductRecommendation> recommendations =
                recommendationEngine().getRecommendations(cpuCategory(), inventory.withEnabledSuppliersOnly(STORE_ID));

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
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", ProductCategory.CPU, 1));

        // when
        Collection<MatchedInventory> result = inventory.withEnabledSuppliersOnly(STORE_ID).findAllByProductCategory(ProductCategory.CPU.name());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
    }

    @Test
    void findAllByProductCategorySurfacesOwnOnlyProductThroughFullWarehouseChain() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(true);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownActionGroup())));
        stubGlobalIndex(List.of());
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", ProductCategory.CPU, 1));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        when(stockQueryService.searchAvailableByMfns(any(), any())).thenReturn(List.of());

        // when
        Collection<MatchedInventory> result = inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findAllByProductCategory(ProductCategory.CPU.name());

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
    void findAllByProductCategoryAppendsWarehouseStock() {
        // given
        Store store = org.mockito.Mockito.mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("AB Group"));
        when(store.hasOwnSupplierConnections()).thenReturn(false);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        stubGlobalIndex(List.of(new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0)), taxonomyCache, supplierRegistry)));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy(EAN, MFN, "Intel", "i7", ProductCategory.CPU, 1));
        StockQueryService stockQueryService = org.mockito.Mockito.mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView view = org.mockito.Mockito.mock(WarehouseItemView.class);
        when(view.toInventoryItem()).thenReturn(new InventoryItem(EAN, MFN, 1200.0, "PLN", 3, 1, "Warehouse", true, true, false));
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), argThat(mfns -> mfns.contains(MFN)))).thenReturn(List.of(view));

        // when
        Collection<MatchedInventory> result = inventory.withEnabledSuppliersAndWarehouseData(STORE_ID).findAllByProductCategory(ProductCategory.CPU.name());

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
}
