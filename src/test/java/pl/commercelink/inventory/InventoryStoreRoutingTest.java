package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryStoreRoutingTest {

    private final Warehouse warehouse = mock(Warehouse.class);
    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final InventoryAutoDiscovery autoDiscovery = mock(InventoryAutoDiscovery.class);
    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final StoreInventoryProvider storeInventoryProvider = mock(StoreInventoryProvider.class);

    private final Inventory inventory = new Inventory(
            warehouse, storesRepository, autoDiscovery, taxonomyCache, supplierRegistry, storeInventoryProvider);

    private Store store(boolean hasOwn) {
        Store store = mock(Store.class);
        when(store.hasOwnSupplierConnections()).thenReturn(hasOwn);
        when(store.getEnabledProviders()).thenReturn(List.of());
        return store;
    }

    @Test
    void withEnabledSuppliersOnlyQueriesStoreCacheWhenStoreHasOwnConnections() {
        Store store = store(true);
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeInventoryProvider.get("store-1"))
                .thenReturn(new StoreInventory(new LinkedList<>(), LocalDateTime.now()));

        inventory.withEnabledSuppliersOnly("store-1");

        verify(storeInventoryProvider).get("store-1");
    }

    @Test
    void withEnabledSuppliersOnlyUsesGlobalPathWhenStoreHasNoOwnConnections() {
        Store store = store(false);
        when(storesRepository.findById("store-1")).thenReturn(store);

        inventory.withEnabledSuppliersOnly("store-1");

        verify(storeInventoryProvider, never()).get(any());
    }
}
