package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestockSuggestionServiceRoutingTest {

    private static final String STORE_ID = "store-1";
    private static final String SUPPLIER = "supplier-a";

    @Mock
    private Inventory inventory;
    @Mock
    private StockLevels stockLevels;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Store store;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private Warehouse warehouse;

    @Test
    void suggestForDeliveryCallsInventoryWithFulfilmentScope() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.isEnabledSupplier(SUPPLIER)).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of());

        RestockSuggestionService service = new RestockSuggestionService(
                inventory, stockLevels, productCatalogRepository, storesRepository, warehouse);

        // when
        service.suggestForDelivery(STORE_ID, SUPPLIER, Set.of());

        // then
        verify(inventory).withEnabledSuppliersOnly(eq(STORE_ID), eq(SupplierScope.FULFILMENT));
    }
}
