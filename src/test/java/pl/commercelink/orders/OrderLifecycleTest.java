package pl.commercelink.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderLifecycleTest {

    @Mock private StoresRepository storesRepository;
    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock private OrderNotificationsEventPublisher notificationEventPublisher;
    @Mock private DeliveriesRepository deliveriesRepository;
    @Mock private InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Mock private GoodsOutEventPublisher goodsOutEventPublisher;

    @InjectMocks
    private OrderLifecycle orderLifecycle;

    @Test
    void publishesOrderAcceptedWhenNewOrderMovesToAssembly() {
        // given
        Order order = new Order("store-1");
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(true);
        when(item.isDelivered()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Assembly, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderAccepted);
    }

    @Test
    void publishesNoEventWhenStatusDoesNotChange() {
        // given
        Order order = new Order("store-1");
        OrderItem item = mock(OrderItem.class);
        when(item.isOrdered()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        verifyNoInteractions(orderLifecycleEventPublisher);
    }

    @Test
    void publishesOrderCancelledWhenAllItemsReturnedAfterDelivery() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(false).when(order).isSettled(anyBoolean());
        doReturn(mock(OrderReview.class)).when(order).getReview();
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(true);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Cancelled, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCancelled);
    }

    @Test
    void publishesOrderCompletedWhenDeliveredOrderIsSettled() {
        // given
        Order order = spy(new Order("store-1"));
        order.setStatus(OrderStatus.Delivered);
        doReturn(false).when(order).isAwaitingInvoiceGeneration();
        doReturn(false).when(order).isAwaitingDocumentsGeneration(anyBoolean());
        doReturn(true).when(order).isSettled(anyBoolean());
        OrderItem item = mock(OrderItem.class);
        when(item.isReturned()).thenReturn(false);

        // when
        orderLifecycle.update(order, List.of(item));

        // then
        assertEquals(OrderStatus.Completed, order.getStatus());
        verify(orderLifecycleEventPublisher).publish(order, OrderLifecycleEventType.OrderCompleted);
    }
}
