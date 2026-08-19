package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

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
        delivery.setDropship(new Dropship("order-1"));

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
        delivery.setDropship(new Dropship(" "));

        // when / then
        assertFalse(delivery.isDropship());
    }

    @Test
    void dropshipOrderIdReturnsOrderIdForDropshipDelivery() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDropship(new Dropship("order-1"));

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
}
