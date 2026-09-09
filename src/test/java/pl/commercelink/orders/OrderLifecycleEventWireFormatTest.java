package pl.commercelink.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the real Jackson round-trip of {@link OrderLifecycleEvent} over SQS.
 */
class OrderLifecycleEventWireFormatTest {

    @Test
    void orderEventSurvivesTheQueueRoundTrip() throws Exception {
        // given
        ObjectMapper mapper = new ObjectMapper();
        OrderLifecycleEvent event = new OrderLifecycleEvent("store-1", "order-1", OrderLifecycleEventType.OrderAccepted,
                "ALLEGRO-1", "Allegro");

        // when
        OrderLifecycleEvent parsed = mapper.readValue(mapper.writeValueAsString(event), OrderLifecycleEvent.class);

        // then
        assertEquals("store-1", parsed.getStoreId());
        assertEquals("order-1", parsed.getOrderId());
        assertEquals(OrderLifecycleEventType.OrderAccepted, parsed.getType());
        assertEquals("ALLEGRO-1", parsed.getExternalOrderId());
        assertEquals("Allegro", parsed.getMarketplace());
    }
}
