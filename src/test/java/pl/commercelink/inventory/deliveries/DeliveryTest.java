package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

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
}
