package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        pending.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now()));
        Shipment failed = dispatched(ShipmentType.Courier);
        failed.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now()));
        failed.setCancellation(failed.getCancellation().failed());
        Shipment unconfirmed = dispatched(ShipmentType.Courier);
        unconfirmed.setCancellation(CourierCancellation.pending("cmd-1", LocalDateTime.now()));
        unconfirmed.setCancellation(unconfirmed.getCancellation().unconfirmed());

        // then
        assertFalse(none.isCancellationUnresolved());
        assertFalse(pending.isCancellationUnresolved());
        assertTrue(failed.isCancellationUnresolved());
        assertTrue(unconfirmed.isCancellationUnresolved());
    }

    @Test
    void settingTheRememberedCancellationBackPutsBackEveryField() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(3);
        Shipment shipment = dispatched(ShipmentType.Courier);
        shipment.setCancellation(CourierCancellation.pending("cmd-1", requestedAt).failed());
        CourierCancellation previous = shipment.getCancellation();
        shipment.setCancellation(CourierCancellation.pending("cmd-2", LocalDateTime.now()));

        // when
        shipment.setCancellation(previous);

        // then
        assertEquals(ShipmentCancellationStatus.FAILED, shipment.getCancellation().getStatus());
        assertTrue(shipment.getCancellation().hasCommand("cmd-1"));
        assertEquals(requestedAt, shipment.getCancellation().getRequestedAt());
    }

    @Test
    void newShipmentHasNoCancellation() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);
        LocalDateTime now = LocalDateTime.now();

        // then
        assertNull(shipment.getCancellation());
        assertFalse(shipment.isCancellationInProgress(now));
        assertFalse(shipment.needsCancellationRecheck(now));
        assertFalse(shipment.isCancellationUnresolved());
        assertFalse(shipment.isCancellationPendingFor("cmd-1"));
    }

    @Test
    void isCancellationPendingForOnlyWhilePendingForThatCommand() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Shipment pending = dispatched(ShipmentType.Courier);
        pending.setCancellation(CourierCancellation.pending("cmd-1", now));
        Shipment failed = dispatched(ShipmentType.Courier);
        failed.setCancellation(CourierCancellation.pending("cmd-1", now).failed());

        // then
        assertTrue(pending.isCancellationPendingFor("cmd-1"));
        assertFalse(pending.isCancellationPendingFor("cmd-2"));
        assertFalse(failed.isCancellationPendingFor("cmd-1"));
    }

    @Test
    void cancellationStaysWithTheCourierOrderOnEdit() {
        // given
        LocalDateTime requested = LocalDateTime.now();
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setExternalId("21353832");
        saved.setCancellation(CourierCancellation.pending("cmd-1", requested));
        Shipment edited = new Shipment(ShipmentType.Courier);

        // when
        edited.inheritCourierOrderFrom(saved);

        // then
        assertEquals("21353832", edited.getExternalId());
        assertTrue(edited.getCancellation().isPending());
        assertTrue(edited.getCancellation().hasCommand("cmd-1"));
        assertEquals(requested, edited.getCancellation().getRequestedAt());
    }
}
