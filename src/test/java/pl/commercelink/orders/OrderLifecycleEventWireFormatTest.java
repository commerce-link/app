package pl.commercelink.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the real Jackson round-trip of {@link OrderLifecycleEvent} over SQS: no test in this
 * module otherwise touches a plain {@link ObjectMapper}, so a silently dropped or renamed field
 * (e.g. the refund's commandId) would only ever surface in production.
 */
class OrderLifecycleEventWireFormatTest {

    @Test
    void returnActionSurvivesTheQueueRoundTrip() throws Exception {
        // given
        ObjectMapper mapper = new ObjectMapper();
        MarketplaceReturnAction action = new MarketplaceReturnAction("rma-1", "ret-1",
                List.of(new MarketplaceReturnAction.Item("sku-a", 2)), true, "cmd-1", null);
        // externalReturnReference is the newest field: it is only reachable via its setter and is
        // deliberately excluded from the 6-arg constructor, making it the field most likely to be
        // silently dropped by an accidental @JsonIgnore or a future @JsonCreator that omits it.
        action.setExternalReturnReference("XGQX/2026");
        OrderLifecycleEvent event = new OrderLifecycleEvent("store-1", "order-1", OrderLifecycleEventType.ReturnAccepted,
                "ALLEGRO-1", "Allegro", action);

        // when
        OrderLifecycleEvent parsed = mapper.readValue(mapper.writeValueAsString(event), OrderLifecycleEvent.class);

        // then: these five fields are the refund; losing any of them moves the wrong amount of money
        assertEquals("cmd-1", parsed.getReturnAction().getCommandId());
        assertTrue(parsed.getReturnAction().isRefundDelivery());
        assertEquals("sku-a", parsed.getReturnAction().getItems().get(0).getManufacturerCode());
        assertEquals(2, parsed.getReturnAction().getItems().get(0).getQuantity());
        assertEquals("XGQX/2026", parsed.getReturnAction().getExternalReturnReference());
    }

    @Test
    void legacyEventWithoutReturnActionStillDeserialises() throws Exception {
        // given: a message enqueued by the previous release
        String legacy = "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ALLEGRO-1\","
                + "\"marketplace\":\"Allegro\",\"type\":\"OrderAccepted\"}";

        // when
        OrderLifecycleEvent parsed = new ObjectMapper().readValue(legacy, OrderLifecycleEvent.class);

        // then
        assertNull(parsed.getReturnAction());
    }
}
