package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentCancellationStateTest {

    private static final LocalDateTime REQUESTED = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    void newShipmentHasNoCancellation() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);

        // then
        assertThat(shipment.getCancellationStatus()).isNull();
        assertThat(shipment.isCancellationPending()).isFalse();
        assertThat(shipment.isCancellationInProgress(REQUESTED)).isFalse();
        assertThat(shipment.needsCancellationRecheck(REQUESTED)).isFalse();
    }

    @Test
    void pendingIsInProgressUntilFiveMinutesPass() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);

        // when
        shipment.markCancellationPending("cmd-1", REQUESTED);

        // then
        assertThat(shipment.hasCancellationCommand("cmd-1")).isTrue();
        assertThat(shipment.hasCancellationCommand("cmd-2")).isFalse();
        assertThat(shipment.isCancellationInProgress(REQUESTED.plusMinutes(5))).isTrue();
        assertThat(shipment.needsCancellationRecheck(REQUESTED.plusMinutes(5))).isFalse();
        assertThat(shipment.isCancellationInProgress(REQUESTED.plusMinutes(5).plusSeconds(1))).isFalse();
        assertThat(shipment.needsCancellationRecheck(REQUESTED.plusMinutes(5).plusSeconds(1))).isTrue();
        assertThat(shipment.isCancellationPending()).isTrue();
    }

    @Test
    void failedKeepsReasonAndPendingAgainClearsIt() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.markCancellationPending("cmd-1", REQUESTED);

        // when
        shipment.markCancellationFailed("already picked up");

        // then
        assertThat(shipment.getCancellationStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(shipment.getCancellationError()).isEqualTo("already picked up");
        assertThat(shipment.needsCancellationRecheck(REQUESTED)).isFalse();

        // when
        shipment.markCancellationPending("cmd-2", REQUESTED.plusMinutes(1));

        // then
        assertThat(shipment.getCancellationError()).isNull();
        assertThat(shipment.getCancellationCommandId()).isEqualTo("cmd-2");
    }

    @Test
    void unconfirmedNeedsRecheck() {
        // given
        Shipment shipment = new Shipment(ShipmentType.Courier);
        shipment.markCancellationPending("cmd-1", REQUESTED);

        // when
        shipment.markCancellationUnconfirmed();

        // then
        assertThat(shipment.needsCancellationRecheck(REQUESTED)).isTrue();
        assertThat(shipment.isCancellationInProgress(REQUESTED)).isFalse();
        assertThat(shipment.getCancellationCommandId()).isEqualTo("cmd-1");
    }

    @Test
    void cancellationStaysWithTheCourierOrderOnEdit() {
        // given
        Shipment saved = new Shipment(ShipmentType.Courier);
        saved.setExternalId("21353832");
        saved.markCancellationPending("cmd-1", REQUESTED);
        Shipment edited = new Shipment(ShipmentType.Courier);

        // when
        edited.inheritCourierOrderFrom(saved);

        // then
        assertThat(edited.getExternalId()).isEqualTo("21353832");
        assertThat(edited.isCancellationPending()).isTrue();
        assertThat(edited.hasCancellationCommand("cmd-1")).isTrue();
        assertThat(edited.getCancellationRequestedAt()).isEqualTo(REQUESTED);
    }
}
