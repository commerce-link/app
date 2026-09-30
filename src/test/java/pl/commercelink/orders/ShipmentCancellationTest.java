package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentCancellationTest {

    private static final LocalDateTime REQUESTED = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    void pendingIsInProgressUntilFiveMinutesPass() {
        // when
        ShipmentCancellation cancellation = ShipmentCancellation.pending("cmd-1", REQUESTED);

        // then
        assertThat(cancellation.getStatus()).isEqualTo(ShipmentCancellationStatus.PENDING);
        assertThat(cancellation.isPending()).isTrue();
        assertThat(cancellation.getRequestedAt()).isEqualTo(REQUESTED);
        assertThat(cancellation.isInProgress(REQUESTED.plus(ShipmentCancellation.STALE))).isTrue();
        assertThat(cancellation.needsRecheck(REQUESTED.plus(ShipmentCancellation.STALE))).isFalse();
        assertThat(cancellation.isInProgress(REQUESTED.plusMinutes(5).plusSeconds(1))).isFalse();
        assertThat(cancellation.needsRecheck(REQUESTED.plusMinutes(5).plusSeconds(1))).isTrue();
        assertThat(cancellation.isUnresolved()).isFalse();
    }

    @Test
    void aPendingOneWithoutARequestTimeIsStale() {
        // given
        ShipmentCancellation cancellation = ShipmentCancellation.pending("cmd-1", null);

        // then
        assertThat(cancellation.isInProgress(REQUESTED)).isFalse();
        assertThat(cancellation.needsRecheck(REQUESTED)).isTrue();
    }

    @Test
    void hasCommandMatchesOnlyItsOwnCommand() {
        // given
        ShipmentCancellation cancellation = ShipmentCancellation.pending("cmd-1", REQUESTED);

        // then
        assertThat(cancellation.hasCommand("cmd-1")).isTrue();
        assertThat(cancellation.hasCommand("cmd-2")).isFalse();
        assertThat(cancellation.hasCommand(null)).isFalse();
        assertThat(new ShipmentCancellation().hasCommand(null)).isFalse();
    }

    @Test
    void failedKeepsTheCommandAndLeavesTheEarlierStateAsItWas() {
        // given
        ShipmentCancellation pending = ShipmentCancellation.pending("cmd-1", REQUESTED);

        // when
        ShipmentCancellation failed = pending.failed();

        // then
        assertThat(failed.getStatus()).isEqualTo(ShipmentCancellationStatus.FAILED);
        assertThat(failed.hasCommand("cmd-1")).isTrue();
        assertThat(failed.getRequestedAt()).isEqualTo(REQUESTED);
        assertThat(failed.isUnresolved()).isTrue();
        assertThat(failed.needsRecheck(REQUESTED)).isFalse();
        assertThat(failed.isInProgress(REQUESTED)).isFalse();
        assertThat(pending.isPending()).isTrue();
    }

    @Test
    void aNewCommandAfterAFailureStartsFromScratch() {
        // given
        ShipmentCancellation failed = ShipmentCancellation.pending("cmd-1", REQUESTED).failed();

        // when
        ShipmentCancellation again = ShipmentCancellation.pending("cmd-2", REQUESTED.plusMinutes(1));

        // then
        assertThat(again.getStatus()).isEqualTo(ShipmentCancellationStatus.PENDING);
        assertThat(again.getCommandId()).isEqualTo("cmd-2");
        assertThat(failed.getCommandId()).isEqualTo("cmd-1");
    }

    @Test
    void unconfirmedNeedsRecheck() {
        // given
        ShipmentCancellation pending = ShipmentCancellation.pending("cmd-1", REQUESTED);

        // when
        ShipmentCancellation unconfirmed = pending.unconfirmed();

        // then
        assertThat(unconfirmed.getStatus()).isEqualTo(ShipmentCancellationStatus.UNCONFIRMED);
        assertThat(unconfirmed.needsRecheck(REQUESTED)).isTrue();
        assertThat(unconfirmed.isInProgress(REQUESTED)).isFalse();
        assertThat(unconfirmed.isUnresolved()).isTrue();
        assertThat(unconfirmed.getCommandId()).isEqualTo("cmd-1");
        assertThat(pending.isPending()).isTrue();
    }
}
