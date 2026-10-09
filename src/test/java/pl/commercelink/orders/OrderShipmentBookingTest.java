package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderShipmentBookingTest {

    private static Order orderWith(Shipment... shipments) {
        Order order = new Order("store-1");
        order.setShipments(new ArrayList<>(List.of(shipments)));
        return order;
    }

    private static Shipment creating() {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId("21480003");
        s.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));
        return s;
    }

    @Test
    void anOrderWhoseOnlyShipmentIsBeingCreatedHasNothingToBook() {
        // when / then
        assertThat(orderWith(creating()).hasShipmentToBook()).isFalse();
    }

    @Test
    void aFailedCreationCanBeBookedAgainEvenWithAnExternalId() {
        // given
        Shipment failed = creating();
        failed.setCreation(failed.getCreation().failed("Błąd przewoźnika"));

        // when / then
        assertThat(orderWith(failed).hasShipmentToBook()).isTrue();
    }

    @Test
    void aShipmentBeingCreatedIsNotTheCourierOrderToCancel() {
        // when / then
        assertThat(orderWith(creating()).courierShipmentToCancel()).isEmpty();
    }

    @Test
    void aShipmentBeingCreatedIsNotAPlaceholder() {
        // given
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setCreation(ShipmentCreationState.pending("cmd-1", LocalDateTime.now()));

        // when / then
        assertThat(s.isPlaceholder()).isFalse();
    }

    @Test
    void hasShipmentBeingCreatedIsTrueOnlyWhileACreationIsPending() {
        // given
        Shipment failed = creating();
        failed.setCreation(failed.getCreation().failed("Błąd"));

        // when / then
        assertThat(orderWith(creating()).hasShipmentBeingCreated()).isTrue();
        assertThat(orderWith(failed).hasShipmentBeingCreated()).isFalse();
        assertThat(orderWith(new Shipment(ShipmentType.Courier)).hasShipmentBeingCreated()).isFalse();
    }
}
