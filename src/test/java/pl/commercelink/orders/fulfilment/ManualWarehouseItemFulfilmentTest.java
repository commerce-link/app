package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.WarehouseFulfilmentService;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualWarehouseItemFulfilmentTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private WarehouseFulfilmentService warehouseFulfilmentService;
    @Mock
    private Order order;

    @InjectMocks
    private ManualWarehouseItemFulfilment fulfilment;

    @Test
    void sendsOrderItemToWarehouseFulfilmentWithTheRequestedWarehouseItem() {
        // given
        OrderItem orderItem = new OrderItem(ORDER_ID, Categories.UNCATEGORIZED, "Widget", 1, 199.0, "MFN-1", false);
        WarehouseItemView warehouseItem = new WarehouseItemView(
                STORE_ID, "warehouse-item-1", "5901234123457", "MFN-1", Price.fromNet(20.0), 1, FulfilmentStatus.Delivered, ItemCondition.Sealed
        );
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(warehouseFulfilmentService.run(eq(order), any())).thenReturn(List.of(orderItem));

        // when
        fulfilment.run(STORE_ID, orderItem, warehouseItem);

        // then
        ArgumentCaptor<List<OrderItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(warehouseFulfilmentService).run(eq(order), captor.capture());
        OrderItem sent = captor.getValue().get(0);
        assertEquals(OrderItem.GENERIC_WAREHOUSE_ORDER_NO, sent.getDeliveryId());
        assertEquals("MFN-1", sent.getManufacturerCode());
        assertEquals("5901234123457", sent.getEan());
        assertEquals("warehouse-item-1", sent.getWarehouseItemId());
        verify(orderItemsRepository).batchSave(List.of(orderItem));
    }
}
