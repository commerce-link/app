package pl.commercelink.warehouse.builtin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WarehouseItemClaimedDeliveryIdTest {

    @Test
    void returnToAllocationPoolClearsClaimedDeliveryId() {
        // given
        WarehouseItem item = new WarehouseItem("store-1", "Acme", "cat", "Product", "EAN-1", "MFN-1", 10.0, 1);
        item.markAsOrdered("d-8f3a", 10.0);

        // when
        item.returnToAllocationPool("Acme");

        // then
        assertNull(item.getClaimedDeliveryId());
    }

    @Test
    void splitOffCopiesClaimedDeliveryId() {
        // given
        WarehouseItem item = new WarehouseItem("store-1", "Acme", "cat", "Product", "EAN-1", "MFN-1", 10.0, 5);
        item.markAsOrdered("d-8f3a", 10.0);

        // when
        WarehouseItem splitItem = item.splitOff(2);

        // then
        assertEquals("d-8f3a", splitItem.getClaimedDeliveryId());
    }

    @Test
    void updateCopiesClaimedDeliveryIdForNewItems() {
        // given
        WarehouseItem target = new WarehouseItem("store-1", "Acme", "cat", "Product", "EAN-1", "MFN-1", 10.0, 1);
        WarehouseItem other = new WarehouseItem("store-1", "Acme", "cat", "Product", "EAN-1", "MFN-1", 10.0, 1);
        other.markAsOrdered("d-8f3a", 10.0);

        // when
        target.update(other);

        // then
        assertEquals("d-8f3a", target.getClaimedDeliveryId());
    }
}
