package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void onlyAFailedOrUnconfirmedCancellationIsUnresolved() {
        // given
        Shipment none = dispatched(ShipmentType.Courier);
        Shipment pending = dispatched(ShipmentType.Courier);
        pending.markCancellationPending("cmd-1", LocalDateTime.now());
        Shipment failed = dispatched(ShipmentType.Courier);
        failed.markCancellationPending("cmd-1", LocalDateTime.now());
        failed.markCancellationFailed("refused");
        Shipment unconfirmed = dispatched(ShipmentType.Courier);
        unconfirmed.markCancellationPending("cmd-1", LocalDateTime.now());
        unconfirmed.markCancellationUnconfirmed();

        // then
        assertFalse(none.isCancellationUnresolved());
        assertFalse(pending.isCancellationUnresolved());
        assertTrue(failed.isCancellationUnresolved());
        assertTrue(unconfirmed.isCancellationUnresolved());
    }

    @Test
    void restoreCancellationPutsBackEveryField() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(3);
        Shipment shipment = dispatched(ShipmentType.Courier);
        shipment.markCancellationPending("cmd-2", LocalDateTime.now());

        // when
        shipment.restoreCancellation(ShipmentCancellationStatus.FAILED, "cmd-1", "refused", requestedAt);

        // then
        assertEquals(ShipmentCancellationStatus.FAILED, shipment.getCancellationStatus());
        assertTrue(shipment.hasCancellationCommand("cmd-1"));
        assertEquals("refused", shipment.getCancellationError());
        assertEquals(requestedAt, shipment.getCancellationRequestedAt());
    }
}
