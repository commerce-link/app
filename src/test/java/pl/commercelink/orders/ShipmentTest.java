package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipmentTest {

    private Shipment dispatched(ShipmentType type) {
        Shipment shipment = new Shipment(type);
        shipment.setCarrier("InPost");
        shipment.setTrackingNo("6205123456");
        shipment.setShippedAt(LocalDateTime.now());
        return shipment;
    }

    @Test
    void courierShipmentWithTrackingHasShippingData() {
        // when / then
        assertTrue(dispatched(ShipmentType.Courier).hasShippingData());
    }

    @Test
    void pickupPointShipmentWithTrackingHasShippingData() {
        // when / then
        assertTrue(dispatched(ShipmentType.PickupPoint).hasShippingData());
    }

    @Test
    void personalCollectionNeverCountsAsCarrierShipment() {
        // when / then
        assertFalse(dispatched(ShipmentType.PersonalCollection).hasShippingData());
    }

    @Test
    void pickupPointWithoutTrackingHasNoShippingData() {
        // given
        Shipment shipment = new Shipment(ShipmentType.PickupPoint);

        // when / then
        assertFalse(shipment.hasShippingData());
    }
}
