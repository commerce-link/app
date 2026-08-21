package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

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
}
