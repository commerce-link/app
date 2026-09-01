package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryTest {

    @Test
    void reportsOrderStatusFlags() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");

        // when / then
        assertFalse(delivery.isOrderPending());
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_PENDING);
        assertTrue(delivery.isOrderPending());
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        assertTrue(delivery.isOrderFailed());
    }

    @Test
    void isDropshipReturnsTrueForDropshipType() {
        // given
        Delivery delivery = new Delivery();
        delivery.setType(DeliveryType.DROPSHIP);

        // when / then
        assertTrue(delivery.isDropship());
    }

    @Test
    void typeDefaultsToWarehouseWhenAttributeIsAbsent() {
        // given
        Delivery delivery = new Delivery();
        delivery.setType(null);

        // when / then
        assertEquals(DeliveryType.WAREHOUSE, delivery.getType());
        assertFalse(delivery.isDropship());
    }

    @Test
    void dropshipPropertyBindsToBooleanInSpel() {
        // given
        Delivery dropship = new Delivery();
        dropship.setType(DeliveryType.DROPSHIP);
        Delivery warehouse = new Delivery();
        SpelExpressionParser parser = new SpelExpressionParser();

        // when / then
        assertEquals(Boolean.TRUE, parser.parseExpression("dropship and true")
                .getValue(new StandardEvaluationContext(dropship), Boolean.class));
        assertEquals(Boolean.FALSE, parser.parseExpression("dropship and true")
                .getValue(new StandardEvaluationContext(warehouse), Boolean.class));
    }

    @Test
    void trackableRequiresOpenDropshipWithSupplierOrderNumber() {
        // given
        Delivery delivery = new Delivery("s1", "ACME-DS-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);

        // when / then
        assertTrue(delivery.isTrackable());
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        assertFalse(delivery.isTrackable());
        delivery.setOrderStatus(null);
        delivery.markAsReceived();
        assertFalse(delivery.isTrackable());
    }

    @Test
    void trackableRejectsWarehouseAndMissingExternalId() {
        // given
        Delivery warehouse = new Delivery("s1", "PO-1", "Acme");
        Delivery noNumber = new Delivery("s1", " ", "Acme");
        noNumber.setType(DeliveryType.DROPSHIP);

        // when / then
        assertFalse(warehouse.isTrackable());
        assertFalse(noNumber.isTrackable());
    }

    @Test
    void trackingPendingWhenStateMissingOrPending() {
        // given
        Delivery delivery = new Delivery();

        // when / then
        assertTrue(delivery.isTrackingPending());
        assertEquals(DeliveryTrackingState.PENDING, delivery.getTrackingView().effectiveState());
        delivery.tracking().setState(DeliveryTrackingState.PENDING);
        assertTrue(delivery.isTrackingPending());
        delivery.tracking().setState(DeliveryTrackingState.GIVEN_UP);
        assertFalse(delivery.isTrackingPending());
        assertEquals(DeliveryTrackingState.GIVEN_UP, delivery.getTrackingView().effectiveState());
    }

    @Test
    void deliveryWithoutTrackingKeepsTheAttributeUnwritten() {
        // given
        Delivery delivery = new Delivery();

        // when / then
        assertNull(delivery.getTracking());
        assertTrue(delivery.isTrackingPending());
        assertNotNull(delivery.getTrackingView());
        assertNull(delivery.getTracking());
        assertNotNull(delivery.tracking());
        assertNotNull(delivery.getTracking());
    }

    @Test
    void trackablePropertyBindsToBooleanInSpel() {
        // given
        Delivery delivery = new Delivery("s1", "ACME-DS-1", "Acme");
        delivery.setType(DeliveryType.DROPSHIP);
        SpelExpressionParser parser = new SpelExpressionParser();

        // when / then
        assertEquals(Boolean.TRUE, parser.parseExpression("trackable and trackingPending")
                .getValue(new StandardEvaluationContext(delivery), Boolean.class));
    }

    @Test
    void trackingViewResolvesTheTemplateExpressionsInSpel() {
        // given
        Delivery delivery = new Delivery("s1", "ACME-DS-1", "Acme");
        SpelExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext(delivery);

        // when / then
        assertEquals("PENDING", parser.parseExpression("trackingView.effectiveState().name()")
                .getValue(context, String.class));
        assertNull(parser.parseExpression("trackingView.lastCheckedAt").getValue(context));
        assertNull(delivery.getTracking());
    }

    @Test
    void orderOutcomeIsUnknownWhenDispatchedWithAnErrorMessage() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);
        delivery.setOrderErrorMessage("Supplier did not return an order number");

        // when / then
        assertThat(delivery.isOrderOutcomeUnknown()).isTrue();
    }

    @Test
    void orderOutcomeIsNotUnknownWhileTheOrderIsStillBeingPlaced() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.ORDER_DISPATCHED);

        // when / then
        assertThat(delivery.isOrderOutcomeUnknown()).isFalse();
    }

    @Test
    void orderOutcomeIsNotUnknownForAFailedDeliveryWithAnErrorMessage() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Acme");
        delivery.setOrderStatus(DeliveryOrderStatus.FAILED);
        delivery.setOrderErrorMessage("Supplier rejected the order");

        // when / then
        assertThat(delivery.isOrderOutcomeUnknown()).isFalse();
    }

    @Test
    void aWarehouseDeliveryKnowsItCarriesGoodsMeantForTheCustomer() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Elko");
        delivery.setType(DeliveryType.WAREHOUSE);
        Allocation allocation = mock(Allocation.class);
        when(allocation.isDirectToConsumer()).thenReturn(true);
        delivery.setAllocations(List.of(allocation));

        // when / then
        assertThat(delivery.hasDirectToConsumerAllocations()).isTrue();
    }

    @Test
    void anOrdinaryWarehouseDeliveryCarriesNothingForTheCustomer() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Elko");
        delivery.setType(DeliveryType.WAREHOUSE);
        Allocation allocation = mock(Allocation.class);
        when(allocation.isDirectToConsumer()).thenReturn(false);
        delivery.setAllocations(List.of(allocation));

        // when / then
        assertThat(delivery.hasDirectToConsumerAllocations()).isFalse();
    }

    @Test
    void aDeliveryWithoutAllocationsCarriesNothingForTheCustomer() {
        // given
        Delivery delivery = new Delivery("store-1", null, "Elko");

        // when / then
        assertThat(delivery.hasDirectToConsumerAllocations()).isFalse();
    }
}
