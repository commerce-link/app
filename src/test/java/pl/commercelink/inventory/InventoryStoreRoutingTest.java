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

import java.util.List;

import static org.mockito.Mockito.mock;
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

    private Store store() {
        Store store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of());
        return store;
    }

    @Test
    void withEnabledSuppliersOnlyDelegatesOwnInventoryToProvider() {
        // given
        Store store = store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeInventoryProvider.ownInventory(store)).thenReturn(List.of());

        // when
        inventory.withEnabledSuppliersOnly("store-1");

        // then
        verify(storeInventoryProvider).ownInventory(store);
    }

    @Test
    void withEnabledSuppliersAndWarehouseDataDelegatesOwnInventoryToProvider() {
        // given
        Store store = store();
        when(storesRepository.findById("store-1")).thenReturn(store);
        when(storeInventoryProvider.ownInventory(store)).thenReturn(List.of());

        // when
        inventory.withEnabledSuppliersAndWarehouseData("store-1");

        // then
        verify(storeInventoryProvider).ownInventory(store);
    }
}
