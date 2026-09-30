package pl.commercelink.orders.notifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoreActivity;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNotificationsEventListenerTest {

    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderNotificationsService orderNotificationsService;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private OrderNotificationsEventListener listener;

    @Test
    void sendsNotificationsOfActiveStore() {
        // given
        Order order = new Order();
        when(storeActivity.isActive("store-1")).thenReturn(true);
        when(ordersRepository.findById("store-1", "order-1")).thenReturn(order);

        // when
        listener.handleMessage(new OrderNotificationsEventRequest("store-1", "order-1"));

        // then
        verify(orderNotificationsService).send(order);
    }

    @Test
    void sendsNothingForInactiveStore() {
        // given
        when(storeActivity.isActive("store-1")).thenReturn(false);

        // when
        listener.handleMessage(new OrderNotificationsEventRequest("store-1", "order-1"));

        // then
        verifyNoInteractions(ordersRepository, orderNotificationsService);
    }
}
