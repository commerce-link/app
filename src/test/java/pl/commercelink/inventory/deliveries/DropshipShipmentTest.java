package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.time.LocalDateTime;
import java.util.stream.Stream;

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

    static Stream<Arguments> invalidShipments() {
        return Stream.of(
                Arguments.of("personalCollection", new DropshipShipment(ShipmentType.PersonalCollection, "DPD", "PKG-1", null, SHIPPED_AT),
                        "deliveries.dropship.shipment.error.type"),
                Arguments.of("missingType", new DropshipShipment(null, "DPD", "PKG-1", null, SHIPPED_AT),
                        "deliveries.dropship.shipment.error.type"),
                Arguments.of("blankCarrier", new DropshipShipment(ShipmentType.Courier, "  ", "PKG-1", null, SHIPPED_AT),
                        "deliveries.dropship.shipment.error.carrier"),
                Arguments.of("blankTrackingNumber", new DropshipShipment(ShipmentType.Courier, "DPD", null, null, SHIPPED_AT),
                        "deliveries.dropship.shipment.error.trackingNo"),
                Arguments.of("pickupPointWithoutCollectionPoint", new DropshipShipment(ShipmentType.PickupPoint, "InPost", "PKG-1", " ", SHIPPED_AT),
                        "deliveries.dropship.shipment.error.collectionPoint"),
                Arguments.of("missingShippedAt", new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-1", null, null),
                        "deliveries.dropship.shipment.error.shippedAt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidShipments")
    void invalidShipmentIsRejectedWithTheMessageKeyOfTheBrokenField(String caseName, DropshipShipment shipment, String expectedKey) {
        // when / then
        assertThat(shipment.validationError()).isEqualTo(expectedKey);
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

    @Test
    void applyToSetsTrackingUrlWhenPresent() {
        // given
        Shipment target = new Shipment();
        target.setTrackingUrl("https://old");

        // when
        new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-1", null, SHIPPED_AT, " https://t/PKG-1 ").applyTo(target);

        // then
        assertThat(target.getTrackingUrl()).isEqualTo("https://t/PKG-1");
    }

    @Test
    void applyToKeepsExistingTrackingUrlWhenBlank() {
        // given
        Shipment target = new Shipment();
        target.setTrackingUrl("https://old");

        // when
        new DropshipShipment(ShipmentType.Courier, "DPD", "PKG-1", null, SHIPPED_AT, " ").applyTo(target);

        // then
        assertThat(target.getTrackingUrl()).isEqualTo("https://old");
    }
}
