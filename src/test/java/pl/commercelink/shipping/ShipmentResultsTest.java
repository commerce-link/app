package pl.commercelink.shipping;

import pl.commercelink.orders.ShipmentPickup;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentCreationState;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.shipping.api.ShipmentResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentResultsTest {

    @Test
    void createdShipmentsTakeTheResultAndKeepThePlaceholdersChoice() {
        // given
        Shipment placeholder = new Shipment(ShipmentType.PickupPoint);
        placeholder.setCollectionPointCode("WAW23M");
        placeholder.setProvider("furgonetka");
        placeholder.setPickUpAddressId("addr-1");
        placeholder.setCarrier("DPD Classic");
        placeholder.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        ShipmentResult result = new ShipmentResult("21480003", List.of(
                new ShipmentResult.ShipmentParcelResult("A", "dpd", "https://t/A", true, null),
                new ShipmentResult.ShipmentParcelResult("B", null, null, false, null)), null);

        // when
        List<Shipment> created = ShipmentResults.toShipments(result, placeholder);

        // then
        assertThat(created).hasSize(2);
        Shipment first = created.get(0);
        assertThat(first.getExternalId()).isEqualTo("21480003");
        assertThat(first.getTrackingNo()).isEqualTo("A");
        assertThat(first.getCarrier()).isEqualTo("dpd");
        assertThat(first.getType()).isEqualTo(ShipmentType.PickupPoint);
        assertThat(first.getCollectionPointCode()).isEqualTo("WAW23M");
        assertThat(first.getProvider()).isEqualTo("furgonetka");
        assertThat(first.getPickUpAddressId()).isEqualTo("addr-1");
        assertThat(first.getCreation()).isNull();
        assertThat(first.getShippedAt()).isNotNull();
        assertThat(first.awaitsPickup()).isTrue();
        assertThat(created.get(1).getCarrier()).isEqualTo("DPD Classic");
        assertThat(created.get(1).awaitsPickup()).isFalse();
    }

    @Test
    void aParcelWhoseCourierTheCarrierBookedIsOrderedUnderItsPickupNumberAndWaitsForNothing() {
        // given
        Shipment placeholder = new Shipment(ShipmentType.Courier);
        placeholder.setProvider("furgonetka");
        ShipmentResult result = new ShipmentResult("21486850", List.of(
                new ShipmentResult.ShipmentParcelResult("0000014898901T", "dpd", null, false, "APP/CRIN/13023761")),
                null);

        // when
        List<Shipment> created = ShipmentResults.toShipments(result, placeholder);

        // then
        ShipmentPickup pickup = created.get(0).getPickup();
        assertThat(pickup.isOrdered()).isTrue();
        assertThat(pickup.isBookedByCarrier()).isTrue();
        assertThat(pickup.getPickupId()).isEqualTo("APP/CRIN/13023761");
        assertThat(pickup.getDate()).isNull();
        assertThat(created.get(0).awaitsPickup()).isFalse();
    }
}
