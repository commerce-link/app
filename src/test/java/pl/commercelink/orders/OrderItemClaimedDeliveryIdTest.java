package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderItemClaimedDeliveryIdTest {

    @Test
    void claimStampsClaimedDeliveryId() {
        // given
        OrderItem item = new OrderItem();
        item.setDeliveryId("Elko");
        item.setStatus(FulfilmentStatus.Allocation);

        // when
        item.markAsOrdered("d-8f3a", 10.0);

        // then
        assertEquals("d-8f3a", item.getClaimedDeliveryId());
        assertEquals("d-8f3a", item.getDeliveryId());
    }

    @Test
    void releaseClearsClaimedDeliveryId() {
        // given
        OrderItem item = new OrderItem();
        item.markAsOrdered("d-8f3a", 10.0);

        // when
        item.markAsInAllocation();

        // then
        assertNull(item.getClaimedDeliveryId());
    }

    @Test
    void allocationPhaseNeverSetsClaimedDeliveryId() {
        // given
        OrderItem item = new OrderItem();

        // when
        item.setDeliveryId("Warehouse");
        item.markAsInAllocation();

        // then
        assertNull(item.getClaimedDeliveryId());
    }

    @Test
    void removeFulfilmentClearsClaimedDeliveryId() {
        // given
        OrderItem item = new OrderItem();
        item.markAsOrdered("d-8f3a", 10.0);

        // when
        item.removeFulfilment();

        // then
        assertNull(item.getClaimedDeliveryId());
    }
}
