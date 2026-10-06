package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentListsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 10, 0);

    private static Shipment creating(String commandId) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setCarrier("DPD");
        s.setProvider("furgonetka");
        s.setCreation(ShipmentCreationState.pending(commandId, NOW));
        return s;
    }

    private static Shipment created(String externalId, String trackingNo) {
        Shipment s = new Shipment(ShipmentType.Courier);
        s.setExternalId(externalId);
        s.setTrackingNo(trackingNo);
        s.setPickup(ShipmentPickup.awaiting());
        return s;
    }

    @Test
    void replaceCreatingPutsTheCreatedShipmentsWhereThePlaceholderWas() {
        // given
        Shipment manual = created(null, "MANUAL-1");
        List<Shipment> list = new ArrayList<>(List.of(manual, creating("cmd-1")));

        // when
        boolean replaced = ShipmentLists.replaceCreating(list, "cmd-1",
                List.of(created("21480003", "A"), created("21480003", "B")));

        // then
        assertThat(replaced).isTrue();
        assertThat(list).extracting(Shipment::getTrackingNo).containsExactly("MANUAL-1", "A", "B");
    }

    @Test
    void replaceCreatingForAnotherCommandChangesNothing() {
        // given
        List<Shipment> list = new ArrayList<>(List.of(creating("cmd-2")));

        // when
        boolean replaced = ShipmentLists.replaceCreating(list, "cmd-1", List.of(created("1", "A")));

        // then
        assertThat(replaced).isFalse();
        assertThat(list.get(0).isCreationPendingFor("cmd-2")).isTrue();
    }

    @Test
    void applyPickupChangesEveryRowOfTheExternalId() {
        // given
        List<Shipment> list = new ArrayList<>(List.of(created("21480003", "A"), created("21480003", "B"), created("99", "C")));

        // when
        int changed = ShipmentLists.applyPickup(list, List.of("21480003"), p -> ShipmentPickup.notRequired());

        // then
        assertThat(changed).isEqualTo(2);
        assertThat(list.get(0).getPickup().isAwaiting()).isFalse();
        assertThat(list.get(1).getPickup().isAwaiting()).isFalse();
        assertThat(list.get(2).getPickup().isAwaiting()).isTrue();
    }

    @Test
    void dropFailedCreationsKeepsEverythingElse() {
        // given
        Shipment failed = creating("cmd-1");
        failed.setCreation(failed.getCreation().failed("Błąd"));
        List<Shipment> list = new ArrayList<>(List.of(failed, created("1", "A")));

        // when
        ShipmentLists.dropFailedCreations(list);

        // then
        assertThat(list).extracting(Shipment::getTrackingNo).containsExactly("A");
    }
}
