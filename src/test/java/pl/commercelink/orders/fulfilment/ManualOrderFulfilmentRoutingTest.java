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
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualOrderFulfilmentRoutingTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private Inventory inventory;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private WarehouseFulfilmentService warehouseFulfilmentService;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private OrderItem orderItem;
    @Mock
    private FulfilmentPathSelector pathSelector;

    @Test
    void initCallsInventoryWithFulfilmentScope() {
        // given
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(orderItemsRepository.findByOrderIdAndStatus(ORDER_ID, FulfilmentStatus.New)).thenReturn(List.of(orderItem));

        ManualOrderFulfilment service = new ManualOrderFulfilment(
                inventory, ordersRepository, orderLifecycle, orderItemsRepository,
                warehouseFulfilmentService, supplierRegistry);

        // when
        service.init(STORE_ID, List.of(ORDER_ID), pathSelector, false, false, false);

        // then
        verify(inventory).withEnabledSuppliersAndWarehouseData(eq(STORE_ID), eq(SupplierScope.FULFILMENT));
    }
}
