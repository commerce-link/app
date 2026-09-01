package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderItemFamilyTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock private OrdersRepository ordersRepository;
    @Mock private OrderItemsRepository orderItemsRepository;

    @InjectMocks
    private OrderItemFamily orderItemFamily;

    @Test
    void returnsItemsOfEverySplitOffChild() {
        // given
        Order order = new Order(STORE_ID);
        Order child = new Order(STORE_ID);
        child.setSplitFromOrderId(ORDER_ID);
        when(ordersRepository.findBySplitFromOrderId(STORE_ID, order.getOrderId())).thenReturn(List.of(child));
        OrderItem movedItem = mock(OrderItem.class);
        when(orderItemsRepository.findByOrderId(child.getOrderId())).thenReturn(List.of(movedItem));

        // when
        List<OrderItem> items = orderItemFamily.siblingItems(order);

        // then
        assertEquals(List.of(movedItem), items);
    }

    @Test
    void excludesCancelledChildren() {
        // given: M2 - a cancelled child's items were never fulfilled and must not become matchable
        Order order = new Order(STORE_ID);
        Order cancelledChild = new Order(STORE_ID);
        cancelledChild.setSplitFromOrderId(ORDER_ID);
        cancelledChild.setStatus(OrderStatus.Cancelled);
        when(ordersRepository.findBySplitFromOrderId(STORE_ID, order.getOrderId())).thenReturn(List.of(cancelledChild));

        // when
        List<OrderItem> items = orderItemFamily.siblingItems(order);

        // then
        assertTrue(items.isEmpty());
    }

    @Test
    void returnsEmptyWhenTheOrderWasNeverSplit() {
        // given
        Order order = new Order(STORE_ID);
        when(ordersRepository.findBySplitFromOrderId(STORE_ID, order.getOrderId())).thenReturn(List.of());

        // when
        List<OrderItem> items = orderItemFamily.siblingItems(order);

        // then
        assertTrue(items.isEmpty());
    }
}
