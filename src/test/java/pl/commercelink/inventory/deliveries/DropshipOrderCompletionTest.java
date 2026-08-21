package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropshipOrderCompletionTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";
    private static final String DELIVERY_ID = "delivery-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private OrderLifecycle orderLifecycle;

    @InjectMocks
    private DropshipOrderCompletion completion;

    private static OrderItem item(String deliveryId, FulfilmentStatus status) {
        OrderItem item = new OrderItem();
        item.setDeliveryId(deliveryId);
        item.setStatus(status);
        item.setEan("5900000000001");
        item.setManufacturerCode("MFN-1");
        return item;
    }

    @Test
    void marksOrderedItemsOfTheDeliveryAsReceived() {
        // given
        Order order = new Order();
        order.setOrderId(ORDER_ID);
        OrderItem dropshipped = item(DELIVERY_ID, FulfilmentStatus.Ordered);
        OrderItem fromOtherDelivery = item("other-delivery", FulfilmentStatus.Ordered);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(dropshipped, fromOtherDelivery));

        // when
        completion.markSuppliedByDropship(STORE_ID, ORDER_ID, DELIVERY_ID);

        // then
        assertEquals(FulfilmentStatus.Delivered, dropshipped.getStatus());
        assertEquals(FulfilmentStatus.Ordered, fromOtherDelivery.getStatus());
        verify(orderItemsRepository).save(dropshipped);
        verify(orderItemsRepository, never()).save(fromOtherDelivery);
        verify(orderLifecycle).update(same(order), anyList());
    }

    @Test
    void doesNothingWhenTheOrderIsGone() {
        // given
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(null);

        // when
        completion.markSuppliedByDropship(STORE_ID, ORDER_ID, DELIVERY_ID);

        // then
        verifyNoInteractions(orderItemsRepository, orderLifecycle);
    }
}
