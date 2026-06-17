package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryStoreRoutingTest {

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

    @InjectMocks
    private Inventory inventory;

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
