package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.stores.StoreActivity;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFulfilmentEventListenerTest {

    @Mock private AutomatedOrderFulfilment automatedOrderFulfilment;
    @Mock private OrderItemsRepository orderItemsRepository;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private OrderFulfilmentEventListener listener;

    @Test
    void fulfilsOrderOfActiveStore() {
        // given
        OrderItem item = new OrderItem();
        when(storeActivity.isActive("store-1")).thenReturn(true);
        when(orderItemsRepository.findByOrderId("order-1")).thenReturn(List.of(item));

        // when
        listener.handleMessage(new OrderFulfilmentRequest("store-1", "order-1"));

        // then
        verify(automatedOrderFulfilment).run("store-1", List.of(item));
    }

    @Test
    void leavesOrderOfInactiveStoreUnfulfilled() {
        // given
        when(storeActivity.isActive("store-1")).thenReturn(false);

        // when
        listener.handleMessage(new OrderFulfilmentRequest("store-1", "order-1"));

        // then
        verifyNoInteractions(automatedOrderFulfilment, orderItemsRepository);
    }
}
