package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderAllocationsManagerTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrdersManager ordersManager;

    @InjectMocks
    private OrderAllocationsManager orderAllocationsManager;

    @Test
    @DisplayName("remove resets order status to New when at least one item fulfilment was removed")
    void removeResetsOrderStatusToNewWhenAtLeastOneFulfilmentRemoved() {
        // given
        Order order = orderWithStatus(OrderStatus.Assembly);
        OrderItem allocatedItem = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(allocatedItem);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1"));

        // then
        verify(orderItemsRepository).save(allocatedItem);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.New);
    }

    @Test
    @DisplayName("remove does not touch order when all items are outside Allocation/Ordered states")
    void removeDoesNotTouchOrderWhenAllItemsAreOutsideAllocationOrOrderedStates() {
        // given
        Order order = orderWithStatus(OrderStatus.Delivered);
        OrderItem deliveredItem = orderItemInStatus("item-1", FulfilmentStatus.Delivered);
        OrderItem returnedItem = orderItemInStatus("item-2", FulfilmentStatus.Returned);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(deliveredItem);
        when(orderItemsRepository.findById(ORDER_ID, "item-2")).thenReturn(returnedItem);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1", "item-2"));

        // then
        verify(orderItemsRepository, never()).save(any());
        verify(ordersRepository, never()).save(any());
        verify(ordersRepository, never()).findById(any(), any());
    }

    @Test
    @DisplayName("remove only clears items that are in Allocation/Ordered state and ignores others")
    void removeOnlyClearsItemsThatAreInAllocationOrOrderedStateAndIgnoresRest() {
        // given
        Order order = orderWithStatus(OrderStatus.Assembly);
        OrderItem allocatedItem = orderItemInStatus("item-1", FulfilmentStatus.Allocation);
        OrderItem deliveredItem = orderItemInStatus("item-2", FulfilmentStatus.Delivered);
        when(orderItemsRepository.findById(ORDER_ID, "item-1")).thenReturn(allocatedItem);
        when(orderItemsRepository.findById(ORDER_ID, "item-2")).thenReturn(deliveredItem);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        orderAllocationsManager.remove(STORE_ID, ORDER_ID, List.of("item-1", "item-2"));

        // then
        verify(orderItemsRepository, times(1)).save(allocatedItem);
        verify(orderItemsRepository, never()).save(deliveredItem);
        verify(ordersRepository, times(1)).save(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.New);
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setStatus(status);
        return order;
    }

    private OrderItem orderItemInStatus(String itemId, FulfilmentStatus status) {
        OrderItem item = new OrderItem(ORDER_ID, ProductCategory.Other, "test", 1, 100.0, "SKU-" + itemId, false);
        item.setItemId(itemId);
        item.setStatus(status);
        if (status == FulfilmentStatus.Allocation || status == FulfilmentStatus.Ordered) {
            item.setEan("EAN-" + itemId);
            item.setManufacturerCode("MFN-" + itemId);
            item.setDeliveryId("delivery-1");
        }
        return item;
    }
}
