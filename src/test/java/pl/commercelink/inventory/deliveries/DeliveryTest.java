package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void isDropshipReturnsTrueWhenDropshipAspectCarriesOrderId() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDropshipDetails(new Dropship("order-1"));

        // when / then
        assertTrue(delivery.isDropship());
    }

    @Test
    void isDropshipReturnsFalseWithoutDropshipAspect() {
        // given
        Delivery delivery = new Delivery();

        // when / then
        assertFalse(delivery.isDropship());
    }

    @Test
    void isDropshipReturnsFalseWhenDropshipOrderIdIsBlank() {
        // given
        Delivery delivery = new Delivery();
        Dropship dropship = new Dropship();
        dropship.setOrderId(" ");
        delivery.setDropshipDetails(dropship);

        // when / then
        assertFalse(delivery.isDropship());
    }

    @Test
    void dropshipOrderIdReturnsOrderIdForDropshipDelivery() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDropshipDetails(new Dropship("order-1"));

        // when / then
        assertEquals(Optional.of("order-1"), delivery.dropshipOrderId());
    }

    @Test
    void dropshipOrderIdReturnsEmptyForWarehouseDelivery() {
        // given
        Delivery delivery = new Delivery();

        // when / then
        assertEquals(Optional.empty(), delivery.dropshipOrderId());
    }

    @Test
    void dropshipPropertyBindsToBooleanInSpel() {
        // given
        Delivery dropship = new Delivery();
        dropship.setDropshipDetails(new Dropship("order-1"));
        Delivery warehouse = new Delivery();
        SpelExpressionParser parser = new SpelExpressionParser();

        // when / then
        assertEquals(Boolean.TRUE, parser.parseExpression("dropship and true")
                .getValue(new StandardEvaluationContext(dropship), Boolean.class));
        assertEquals(Boolean.FALSE, parser.parseExpression("dropship and true")
                .getValue(new StandardEvaluationContext(warehouse), Boolean.class));
    }
}
