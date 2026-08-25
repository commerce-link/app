package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DropshipShipmentTest {

    private static final LocalDateTime SHIPPED_AT = LocalDateTime.of(2026, 8, 25, 10, 30);

    private static DropshipShipment courier() {
        return new DropshipShipment(ShipmentType.Courier, " DPD ", " PKG-1 ", null, SHIPPED_AT);
    }

    @Test
    void courierShipmentWithCarrierTrackingAndDateIsValid() {
        // when / then
        assertThat(courier().validationError()).isNull();
    }

    @Test
    void personalCollectionIsRejected() {
        // given
        DropshipShipment shipment = new DropshipShipment(ShipmentType.PersonalCollection, "DPD", "PKG-1", null, SHIPPED_AT);

        // when / then
        assertThat(shipment.validationError()).isEqualTo("deliveries.dropship.shipment.error.type");
    }

    @Test
    void missingTypeIsRejected() {
        // given
        DropshipShipment shipment = new DropshipShipment(null, "DPD", "PKG-1", null, SHIPPED_AT);

        // when / then
        assertThat(shipment.validationError()).isEqualTo("deliveries.dropship.shipment.error.type");
    }

    @Test
    void blankCarrierIsRejected() {
        // given
        DropshipShipment shipment = new DropshipShipment(ShipmentType.Courier, "  ", "PKG-1", null, SHIPPED_AT);

        // when / then
        assertThat(shipment.validationError()).isEqualTo("deliveries.dropship.shipment.error.carrier");
    }

    @Test
    void blankTrackingNumberIsRejected() {
        // given
        DropshipShipment shipment = new DropshipShipment(ShipmentType.Courier, "DPD", null, null, SHIPPED_AT);

        // when / then
        assertThat(shipment.validationError()).isEqualTo("deliveries.dropship.shipment.error.trackingNo");
    }

    @Test
    void pickupPointWithoutCollectionPointIsRejected() {
        // given
        DropshipShipment shipment = new DropshipShipment(ShipmentType.PickupPoint, "InPost", "PKG-1", " ", SHIPPED_AT);

        // when / then
        assertThat(shipment.validationError()).isEqualTo("deliveries.dropship.shipment.error.collectionPoint");
    }

    @Test
    void missingShippedAtIsRejected() {
        // given
        DropshipShipment shipment = new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-1", null, null);

        // when / then
        assertThat(shipment.validationError()).isEqualTo("deliveries.dropship.shipment.error.shippedAt");
    }

    @Test
    void applyToWritesTrimmedValuesAndClearsCollectionPointForCourier() {
        // given
        Shipment shipment = new Shipment();
        shipment.setCollectionPointCode("WAW04A");

        // when
        courier().applyTo(shipment);

        // then
        assertThat(shipment.getType()).isEqualTo(ShipmentType.Courier);
        assertThat(shipment.getCarrier()).isEqualTo("DPD");
        assertThat(shipment.getTrackingNo()).isEqualTo("PKG-1");
        assertThat(shipment.getCollectionPointCode()).isNull();
        assertThat(shipment.getShippedAt()).isEqualTo(SHIPPED_AT);
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.hasShippingData()).isTrue();
    }

    @Test
    void applyToKeepsCollectionPointForPickupPoint() {
        // given
        Shipment shipment = new Shipment();
        DropshipShipment pickup = new DropshipShipment(ShipmentType.PickupPoint, "InPost", "PKG-2", " WAW04A ", SHIPPED_AT);

        // when
        pickup.applyTo(shipment);

        // then
        assertThat(shipment.getType()).isEqualTo(ShipmentType.PickupPoint);
        assertThat(shipment.getCollectionPointCode()).isEqualTo("WAW04A");
        assertThat(shipment.hasShippingData()).isTrue();
    }

    @Test
    void applyToToleratesMissingValues() {
        // given
        Shipment shipment = new Shipment();
        DropshipShipment courierWithoutData = new DropshipShipment(ShipmentType.Courier, null, null, null, null);

        // when
        assertThatCode(() -> courierWithoutData.applyTo(shipment)).doesNotThrowAnyException();

        // then
        assertThat(shipment.getCarrier()).isNull();
        assertThat(shipment.getTrackingNo()).isNull();
    }
}
