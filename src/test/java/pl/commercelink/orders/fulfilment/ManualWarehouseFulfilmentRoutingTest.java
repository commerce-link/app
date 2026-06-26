package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualWarehouseFulfilmentRoutingTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private Inventory inventory;
    @Mock
    private WarehouseAllocationsManager warehouseAllocationsManager;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private InventoryView inventoryView;

    @Test
    void initCallsInventoryWithFulfilmentScope() {
        // given
        when(inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(supplierRegistry.getExternalSupplierNames()).thenReturn(List.of());

        ManualWarehouseFulfilment service = new ManualWarehouseFulfilment(
                inventory, warehouseAllocationsManager, supplierRegistry);

        // when
        service.init(STORE_ID, List.of());

        // then
        verify(inventory).withEnabledSuppliersOnly(eq(STORE_ID), eq(SupplierScope.FULFILMENT));
    }
}
