package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperTableModel;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RMAMarketplaceFieldsTest {

    @Test
    void manuallyCreatedRmaIsNotAMarketplaceReturn() {
        // given
        RMA rma = new RMA("store-1");

        // when / then
        assertFalse(rma.isMarketplaceReturn());
    }

    @Test
    void rmaWithExternalReturnIdIsAMarketplaceReturn() {
        // given
        RMA rma = new RMA("store-1");
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("r-1");
        rma.setExternalReturnReference("XGQX/2026");
        rma.setExternalReturnStatus(MarketplaceReturnStatus.IN_TRANSIT);

        // when / then
        assertTrue(rma.isMarketplaceReturn());
        assertEquals("Allegro", rma.getMarketplace());
        assertEquals(MarketplaceReturnStatus.IN_TRANSIT, rma.getExternalReturnStatus());
    }

    @Test
    void marketplaceReturnStatusIsStoredAsItsEnumName() {
        // given
        RMA rma = new RMA("store-1");
        rma.setExternalReturnStatus(MarketplaceReturnStatus.IN_TRANSIT);

        // when
        Map<String, AttributeValue> attributes = tableModel().convert(rma);

        // then
        assertEquals("IN_TRANSIT", attributes.get("externalReturnStatus").getS());
        assertNull(attributes.get("marketplaceReturn"));
        assertEquals(MarketplaceReturnStatus.IN_TRANSIT,
                tableModel().unconvert(attributes).getExternalReturnStatus());
    }

    @Test
    void marketplaceDecisionsRoundTripThroughTheTableModel() {
        // given
        RMA rma = new RMA("store-1");
        rma.addMarketplaceDecision(new MarketplaceDecision("ReturnAccepted", "cmd-1", "{\"commandId\":\"cmd-1\"}",
                LocalDateTime.of(2026, 9, 4, 12, 0)));

        // when
        Map<String, AttributeValue> attributes = tableModel().convert(rma);
        RMA back = tableModel().unconvert(attributes);

        // then
        assertEquals(1, attributes.get("marketplaceDecisions").getL().size());
        assertEquals("cmd-1", back.getMarketplaceDecisions().get(0).getCommandId());
        assertEquals("ReturnAccepted", back.getMarketplaceDecisions().get(0).getType());
        assertNull(attributes.get("marketplaceActionPayload"));
    }

    @Test
    void actionEventsAreLookedUpByName() {
        // given
        RMA rma = new RMA("store-1");

        // when
        rma.addActionEvent(RMA.EVENT_REFUND_REQUESTED);

        // then
        assertTrue(rma.hasActionEvent(RMA.EVENT_REFUND_REQUESTED));
        assertFalse(rma.hasActionEvent(RMA.EVENT_REJECTION_SENT));
        assertEquals(1, rma.getEvents().size());
        assertEquals(EventType.action, rma.getEvents().get(0).getType());
        assertEquals("RefundRequested", rma.getEvents().get(0).getName());
    }

    @Test
    void hasActionEventIgnoresEventsOfOtherTypes() {
        // given
        RMA rma = new RMA("store-1");
        rma.addEvent(new Event(EventType.email, RMA.EVENT_REFUND_REQUESTED, LocalDateTime.now()));

        // when / then
        assertFalse(rma.hasActionEvent(RMA.EVENT_REFUND_REQUESTED));
    }

    @ParameterizedTest(name = "marketplace={0} current={1} new={2} refundRequested={3} -> blocks={4}")
    @CsvSource({
            "true,  WaitingForItems, Rejected,   true,  true",
            "true,  WaitingForItems, Rejected,   false, false",
            "false, WaitingForItems, Rejected,   true,  false",
            "true,  WaitingForItems, Processing, true,  false",
            "true,  Rejected,        Rejected,   true,  false"})
    void blocksRejectionAfterRefundOnlyWhenAMarketplaceRmaTurnsRejectedAfterARefund(
            boolean marketplace, RMAStatus current, RMAStatus next, boolean refundRequested, boolean expected) {
        // given
        RMA rma = new RMA("store-1");
        rma.setStatus(current);
        if (marketplace) {
            rma.setExternalReturnId("r-1");
        }
        if (refundRequested) {
            rma.addActionEvent(RMA.EVENT_REFUND_REQUESTED);
        }

        // when / then
        assertEquals(expected, rma.blocksRejectionAfterRefund(next));
    }

    @Test
    void rejectionReasonIsRequiredOnlyWhenAMarketplaceRmaTurnsRejected() {
        // given
        RMA marketplace = new RMA("store-1");
        marketplace.setExternalReturnId("r-1");
        marketplace.setStatus(RMAStatus.WaitingForItems);
        RMA manual = new RMA("store-1");

        // when / then
        assertTrue(marketplace.requiresRejectionReason(RMAStatus.Rejected, " "));
        assertTrue(marketplace.requiresRejectionReason(RMAStatus.Rejected, null));
        assertTrue(marketplace.requiresRejectionReason(RMAStatus.Rejected, "x".repeat(251)));
        assertFalse(marketplace.requiresRejectionReason(RMAStatus.Rejected, "Damaged"));
        assertFalse(marketplace.requiresRejectionReason(RMAStatus.Processing, null));
        assertFalse(manual.requiresRejectionReason(RMAStatus.Rejected, null));
        marketplace.setStatus(RMAStatus.Rejected);
        assertFalse(marketplace.requiresRejectionReason(RMAStatus.Rejected, null));
    }

    private DynamoDBMapperTableModel<RMA> tableModel() {
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        return mapper.getTableModel(RMA.class, DynamoDBMapperConfig.DEFAULT);
    }
}
