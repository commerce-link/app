package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentPickupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 10, 0);

    @Test
    void pendingKeepsTheChosenWindowAndOrderedKeepsItWithThePickupId() {
        // given
        ShipmentPickup pending = ShipmentPickup.pending("cmd-1", NOW, LocalDate.of(2026, 10, 7),
                LocalTime.of(9, 0), LocalTime.of(17, 0));

        // when
        ShipmentPickup ordered = pending.ordered("20261006800071");

        // then
        assertThat(pending.isPendingFor("cmd-1")).isTrue();
        assertThat(ordered.isOrdered()).isTrue();
        assertThat(ordered.getPickupId()).isEqualTo("20261006800071");
        assertThat(ordered.getDate()).isEqualTo("2026-10-07");
        assertThat(ordered.getFrom()).isEqualTo("09:00");
        assertThat(ordered.getTo()).isEqualTo("17:00");
        assertThat(pending.getPickupId()).isNull();
    }

    @Test
    void aFailedPickupCanBeOrderedAgain() {
        // when
        ShipmentPickup failed = ShipmentPickup.pending("cmd-1", NOW, LocalDate.of(2026, 10, 7),
                LocalTime.of(9, 0), LocalTime.of(17, 0)).failed("Termin niedostępny");

        // then
        assertThat(failed.isAwaiting()).isTrue();
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.getError()).isEqualTo("Termin niedostępny");
        assertThat(failed.isPendingFor("cmd-1")).isFalse();
    }

    @Test
    void notRequiredIsNeitherAwaitingNorPending() {
        // when
        ShipmentPickup pickup = ShipmentPickup.notRequired();

        // then
        assertThat(pickup.isAwaiting()).isFalse();
        assertThat(pickup.isPending()).isFalse();
    }

    @Test
    void aPickupPendingPastTheTimeoutCanBeOrderedAgainWhileItsCommandStillMatches() {
        // given
        LocalDateTime now = LocalDateTime.now();
        ShipmentPickup fresh = ShipmentPickup.pending("cmd-1", now.minusMinutes(9), LocalDate.of(2026, 10, 7),
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        ShipmentPickup stuck = ShipmentPickup.pending("cmd-1", now.minusMinutes(11), LocalDate.of(2026, 10, 7),
                LocalTime.of(9, 0), LocalTime.of(17, 0));

        // then
        assertThat(fresh.isAwaiting()).isFalse();
        assertThat(fresh.isInProgress(now)).isTrue();
        assertThat(stuck.isAwaiting()).isTrue();
        assertThat(stuck.isInProgress(now)).isFalse();
        assertThat(stuck.isUnconfirmed(now)).isTrue();
        assertThat(stuck.isPendingFor("cmd-1")).isTrue();
    }
}
