package pl.commercelink.orders;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleEventPublisherTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock private SqsTemplate sqsTemplate;
    @Mock private Order order;

    @InjectMocks
    private OrderLifecycleEventPublisher publisher;

    @Test
    void marketplaceOrderEventCarriesExternalOrderIdAndMarketplace() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "prod");
        when(order.isMarketplaceOrder()).thenReturn(true);
        when(order.getStoreId()).thenReturn(STORE_ID);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getExternalOrderId()).thenReturn("EXT-1");
        OrderSource source = mock(OrderSource.class);
        when(source.getName()).thenReturn("Empik");
        when(order.getSource()).thenReturn(source);

        // when
        publisher.publish(order, OrderLifecycleEventType.ShipmentCreated);

        // then
        ArgumentCaptor<OrderLifecycleEvent> captor = ArgumentCaptor.forClass(OrderLifecycleEvent.class);
        verify(sqsTemplate).send(eq("marketplace-order-lifecycle-queue"), captor.capture());
        assertEquals(STORE_ID, captor.getValue().getStoreId());
        assertEquals(ORDER_ID, captor.getValue().getOrderId());
        assertEquals(OrderLifecycleEventType.ShipmentCreated, captor.getValue().getType());
        assertEquals("EXT-1", captor.getValue().getExternalOrderId());
        assertEquals("Empik", captor.getValue().getMarketplace());
    }

    @Test
    void eventIsNotPublishedOutsideProdEnvironment() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "localhost");

        // when
        publisher.publish(order, OrderLifecycleEventType.ShipmentCreated);

        // then
        verifyNoInteractions(sqsTemplate);
    }

    @Test
    void nonMarketplaceOrderEventIsNotPublished() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "prod");
        when(order.isMarketplaceOrder()).thenReturn(false);

        // when
        publisher.publish(order, OrderLifecycleEventType.ShipmentCreated);

        // then
        verifyNoInteractions(sqsTemplate);
    }
}
