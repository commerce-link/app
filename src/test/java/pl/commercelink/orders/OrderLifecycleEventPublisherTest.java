package pl.commercelink.orders;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.orders.rma.RMA;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void returnAcceptedEventCarriesTheReturnAction() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "prod");
        when(order.isMarketplaceOrder()).thenReturn(true);
        when(order.getStoreId()).thenReturn(STORE_ID);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getExternalOrderId()).thenReturn("EXT-1");
        OrderSource source = mock(OrderSource.class);
        when(source.getName()).thenReturn("Allegro");
        when(order.getSource()).thenReturn(source);
        RMA rma = new RMA(STORE_ID);
        rma.setExternalReturnId("r-1");
        MarketplaceReturnAction action = new MarketplaceReturnAction(rma.getRmaId(), "r-1",
                List.of(new MarketplaceReturnAction.Item("SKU-1", 2)), true, "cmd-1", null);

        // when
        publisher.publishReturnAction(order, rma, OrderLifecycleEventType.ReturnAccepted, action);

        // then
        ArgumentCaptor<OrderLifecycleEvent> captor = ArgumentCaptor.forClass(OrderLifecycleEvent.class);
        verify(sqsTemplate).send(eq("marketplace-order-lifecycle-queue"), captor.capture());
        OrderLifecycleEvent event = captor.getValue();
        assertEquals(OrderLifecycleEventType.ReturnAccepted, event.getType());
        assertEquals("EXT-1", event.getExternalOrderId());
        assertEquals("Allegro", event.getMarketplace());
        assertEquals("r-1", event.getReturnAction().getExternalReturnId());
        assertEquals("cmd-1", event.getReturnAction().getCommandId());
        assertTrue(event.getReturnAction().isRefundDelivery());
        assertEquals("SKU-1", event.getReturnAction().getItems().get(0).getManufacturerCode());
        assertEquals(2, event.getReturnAction().getItems().get(0).getQuantity());
    }

    @Test
    void returnActionIsNotPublishedForManualRma() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "prod");
        when(order.isMarketplaceOrder()).thenReturn(true);
        RMA manualRma = new RMA(STORE_ID);

        // when
        publisher.publishReturnAction(order, manualRma, OrderLifecycleEventType.ReturnRejected,
                new MarketplaceReturnAction(manualRma.getRmaId(), null, List.of(), false, null, "reason"));

        // then
        verifyNoInteractions(sqsTemplate);
    }

    @Test
    void orderEventsCarryNoReturnAction() {
        // given
        ReflectionTestUtils.setField(publisher, "env", "prod");
        when(order.isMarketplaceOrder()).thenReturn(true);
        when(order.getStoreId()).thenReturn(STORE_ID);
        when(order.getOrderId()).thenReturn(ORDER_ID);
        when(order.getExternalOrderId()).thenReturn("EXT-1");
        OrderSource source = mock(OrderSource.class);
        when(source.getName()).thenReturn("Allegro");
        when(order.getSource()).thenReturn(source);

        // when
        publisher.publish(order, OrderLifecycleEventType.OrderAccepted);

        // then
        ArgumentCaptor<OrderLifecycleEvent> captor = ArgumentCaptor.forClass(OrderLifecycleEvent.class);
        verify(sqsTemplate).send(eq("marketplace-order-lifecycle-queue"), captor.capture());
        assertNull(captor.getValue().getReturnAction());
    }
}
