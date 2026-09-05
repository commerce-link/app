package pl.commercelink.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the real Jackson round-trip of {@link OrderLifecycleEvent} over SQS and proves a plain
 * {@link ObjectMapper} (as used by MarketplaceReturnDecisions.resendDecisions) reads the record
 * without extra modules.
 */
class OrderLifecycleEventWireFormatTest {

    @Test
    void returnActionSurvivesTheQueueRoundTrip() throws Exception {
        // given
        ObjectMapper mapper = new ObjectMapper();
        MarketplaceReturnAction action = new MarketplaceReturnAction("rma-1", "ret-1",
                List.of(new MarketplaceReturnAction.Item("sku-a", 2)), true, "cmd-1", null);
        OrderLifecycleEvent event = new OrderLifecycleEvent("store-1", "order-1", OrderLifecycleEventType.ReturnAccepted,
                "ALLEGRO-1", "Allegro", action);

        // when
        OrderLifecycleEvent parsed = mapper.readValue(mapper.writeValueAsString(event), OrderLifecycleEvent.class);

        // then: these four fields are the refund; losing any of them moves the wrong amount of money
        assertEquals("cmd-1", parsed.getReturnAction().commandId());
        assertTrue(parsed.getReturnAction().refundDelivery());
        assertEquals("sku-a", parsed.getReturnAction().items().get(0).marketplaceKey());
        assertEquals(2, parsed.getReturnAction().items().get(0).quantity());
    }

    @Test
    void storedReturnActionPayloadsSurviveUnknownFieldsFromNewerReleases() throws Exception {
        // given
        String payload = "{\"rmaId\":\"r\",\"externalReturnId\":\"r-1\",\"items\":[{\"marketplaceKey\":\"K\",\"quantity\":1,\"futureItemField\":true}],\"refundDelivery\":false,\"commandId\":\"c\",\"futureField\":1}";

        // when
        MarketplaceReturnAction action = new ObjectMapper().readValue(payload, MarketplaceReturnAction.class);

        // then
        assertEquals("c", action.commandId());
        assertEquals("K", action.items().get(0).marketplaceKey());
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

    @Test
    void storedReturnActionPayloadWithoutItemsReadsAsEmptyList() throws Exception {
        // given: a rejection payload never carries items
        String payload = "{\"rmaId\":\"r\",\"externalReturnId\":\"r-1\",\"refundDelivery\":false,\"rejectionReason\":\"Damaged\"}";

        // when
        MarketplaceReturnAction action = new ObjectMapper().readValue(payload, MarketplaceReturnAction.class);

        // then
        assertEquals(List.of(), action.items());
    }
}
