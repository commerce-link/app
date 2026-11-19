package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.pim.ScopedPimCatalog.globalEntry;
import static pl.commercelink.pim.ScopedPimCatalog.storeEntry;

/** Every view a store asks for describes products with the store's PIM entries; the global view with global ones. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryProductDataScopeTest {

    private static final String STORE_ID = "s1";
    private static final String OTHER_STORE_ID = "s2";
    private static final String EAN = "5900000000101";

    @Mock
    private Warehouse warehouse;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InventoryAutoDiscovery autoDiscovery;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StoreInventoryProvider storeInventoryProvider;
    @Mock
    private GlobalMatchedInventory globalInventory;
    @Mock
    private StockQueryService stockQueryService;

    private Inventory inventory;

    @BeforeEach
    void createInventory() {
        PimCatalog pim = ScopedPimCatalog.of(globalEntry("g1", EAN, "M-1", "CPU"),
                storeEntry("st-1", STORE_ID, EAN, "M-1", "CPU"));
        TaxonomyCache taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));
        inventory = new Inventory(warehouse, storesRepository, autoDiscovery, supplierRegistry, storeInventoryProvider,
                globalInventory, pim, InventoryMatchingProperties.allEnabled(), new ProductDataResolver(pim, taxonomy));
        InventoryItem offer = new InventoryItem(EAN, "M-1", 100.0, "PLN", 5, 1, "Elko", true, true, false);
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of(
                new MatchedInventory(new InventoryKey(EAN, "M-1"), List.of(offer), supplierRegistry))));
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        storeWithoutOwnOffers(STORE_ID);
        storeWithoutOwnOffers(OTHER_STORE_ID);
    }

    @Test
    void everyViewOfTheStoreDescribesTheProductWithTheStoreEntry() {
        // given
        List<Function<Inventory, InventoryView>> storeViews = List.of(
                inv -> inv.withEnabledSuppliersOnly(STORE_ID),
                inv -> inv.withEnabledSuppliersAndWarehouseData(STORE_ID),
                inv -> inv.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT),
                inv -> inv.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.FULFILMENT),
                inv -> inv.withWarehouseDataOnly(STORE_ID));

        // when
        List<String> names = storeViews.stream()
                .map(view -> view.apply(inventory).findByEan(EAN).getProductData().name())
                .toList();

        // then
        assertThat(names).containsOnly("Store st-1").hasSize(5);
    }

    @Test
    void globalViewDescribesTheProductWithTheGlobalEntry() {
        // when
        String name = inventory.withGlobalData().findByEan(EAN).getProductData().name();

        // then
        assertThat(name).isEqualTo("Global g1");
    }

    @Test
    void viewOfAnotherStoreDoesNotSeeTheStoreEntry() {
        // when
        String name = inventory.withEnabledSuppliersOnly(OTHER_STORE_ID).findByEan(EAN).getProductData().name();

        // then
        assertThat(name).isEqualTo("Global g1");
    }

    private void storeWithoutOwnOffers(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        when(storesRepository.findById(storeId)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
    }
}
