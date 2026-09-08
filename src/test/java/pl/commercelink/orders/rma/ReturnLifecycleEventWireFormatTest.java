package pl.commercelink.orders.rma;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.MarketplaceReturnAction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnLifecycleEventWireFormatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void aRefundDecisionSurvivesTheQueueRoundTrip() throws Exception {
        // given
        ReturnLifecycleEvent event = new ReturnLifecycleEvent("store-1", "order-1", "ext-1", "Allegro",
                ReturnLifecycleEventType.ReturnAccepted,
                new MarketplaceReturnAction("rma-1", "r-1",
                        List.of(new MarketplaceReturnAction.Item("SKU-1", 2)), true, "cmd-1", null));

        // when
        ReturnLifecycleEvent read = mapper.readValue(mapper.writeValueAsString(event), ReturnLifecycleEvent.class);

        // then
        assertEquals(event, read);
        assertEquals("cmd-1", read.action().commandId());
    }

    @Test
    void aRejectionDecisionSurvivesTheQueueRoundTrip() throws Exception {
        // given
        ReturnLifecycleEvent event = new ReturnLifecycleEvent("store-1", "order-1", "ext-1", "Allegro",
                ReturnLifecycleEventType.ReturnRejected,
                new MarketplaceReturnAction("rma-1", "r-1", List.of(), false, null, "Damaged"));

        // when
        ReturnLifecycleEvent read = mapper.readValue(mapper.writeValueAsString(event), ReturnLifecycleEvent.class);

        // then
        assertEquals(event, read);
        assertEquals("Damaged", read.action().rejectionReason());
    }

    @Test
    void aPayloadWithoutItemsReadsAsAnEmptyList() throws Exception {
        // given: the compact constructor must survive Jackson, not only the Java call sites
        String json = "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ext-1\","
                + "\"marketplace\":\"Allegro\",\"type\":\"ReturnRejected\","
                + "\"action\":{\"rmaId\":\"rma-1\",\"externalReturnId\":\"r-1\",\"rejectionReason\":\"Damaged\"}}";

        // when
        ReturnLifecycleEvent read = mapper.readValue(json, ReturnLifecycleEvent.class);

        // then
        assertNotNull(read.action().items());
        assertTrue(read.action().items().isEmpty());
    }

    @Test
    void anUnknownFieldFromANewerBuildIsIgnored() throws Exception {
        // given: a decision persisted by a later version must still replay on this one
        String json = "{\"storeId\":\"store-1\",\"orderId\":\"order-1\",\"externalOrderId\":\"ext-1\","
                + "\"marketplace\":\"Allegro\",\"type\":\"ReturnAccepted\",\"somethingNew\":\"x\","
                + "\"action\":{\"rmaId\":\"rma-1\",\"externalReturnId\":\"r-1\",\"commandId\":\"cmd-1\"}}";

        // when
        ReturnLifecycleEvent read = mapper.readValue(json, ReturnLifecycleEvent.class);

        // then
        assertEquals("cmd-1", read.action().commandId());
    }
}
