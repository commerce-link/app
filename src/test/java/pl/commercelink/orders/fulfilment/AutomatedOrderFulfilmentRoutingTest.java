package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomatedOrderFulfilmentRoutingTest {

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
    private StoresRepository storesRepository;
    @Mock
    private InventoryView inventoryView;
    @Mock
    private OrderItem orderItem;

    @Test
    void runReadsTheStoreAndTheOrdersToHonourTheirSupplierBinding() {
        // given
        when(inventory.withWarehouseDataOnly(STORE_ID)).thenReturn(inventoryView);
        when(orderItem.getOrderId()).thenReturn(ORDER_ID);

        AutomatedOrderFulfilment service = new AutomatedOrderFulfilment(
                inventory, ordersRepository, orderLifecycle, orderItemsRepository,
                warehouseFulfilmentService, storesRepository);

        // when
        service.run(STORE_ID, List.of(orderItem));

        // then
        verify(storesRepository).findById(STORE_ID);
        verify(ordersRepository).findById(STORE_ID, ORDER_ID);
    }
}
