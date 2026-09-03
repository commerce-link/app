package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTrackingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 10, 0);

    @Test
    void freshDocumentIsPendingAndNeverChecked() {
        DeliveryTracking tracking = new DeliveryTracking();

        assertThat(tracking.getState()).isNull();
        assertThat(tracking.isPending()).isTrue();
        assertThat(tracking.effectiveState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(tracking.getLastCheckedAt()).isNull();
        assertThat(tracking.getNextCheckAt()).isNull();
        assertThat(tracking.getAttempts()).isZero();
        assertThat(tracking.getConsecutiveErrors()).isZero();
        assertThat(tracking.getLastError()).isNull();
    }

    @Test
    void terminalStateIsNotPending() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setState(DeliveryTrackingState.GIVEN_UP);

        assertThat(tracking.isPending()).isFalse();
        assertThat(tracking.effectiveState()).isEqualTo(DeliveryTrackingState.GIVEN_UP);
    }

    @Test
    void recordCheckCountsTheAttemptAndClearsTheErrorStreak() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setConsecutiveErrors(3);
        tracking.setLastError("old");
        tracking.setAttempts(2);

        tracking.recordCheck(NOW);

        assertThat(tracking.getLastCheckedAt()).isEqualTo(NOW);
        assertThat(tracking.getAttempts()).isEqualTo(3);
        assertThat(tracking.getConsecutiveErrors()).isZero();
        assertThat(tracking.getLastError()).isNull();
    }

    @Test
    void recordCheckLeavesTheStateAlone() {
        DeliveryTracking tracking = new DeliveryTracking();

        tracking.recordCheck(NOW);

        assertThat(tracking.getState()).isNull();
    }

    @Test
    void recordErrorCountingTowardsGiveUpIncrementsTheStreak() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setConsecutiveErrors(1);

        tracking.recordError("timeout", NOW, true);

        assertThat(tracking.getLastCheckedAt()).isEqualTo(NOW);
        assertThat(tracking.getAttempts()).isEqualTo(1);
        assertThat(tracking.getConsecutiveErrors()).isEqualTo(2);
        assertThat(tracking.getLastError()).isEqualTo("timeout");
    }

    @Test
    void recordErrorNotCountingTowardsGiveUpLeavesTheStreakAlone() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setConsecutiveErrors(4);

        tracking.recordError("timeout", NOW, false);

        assertThat(tracking.getAttempts()).isEqualTo(1);
        assertThat(tracking.getConsecutiveErrors()).isEqualTo(4);
        assertThat(tracking.getLastError()).isEqualTo("timeout");
    }

    @Test
    void recordErrorAbbreviatesALongMessage() {
        DeliveryTracking tracking = new DeliveryTracking();

        tracking.recordError("x".repeat(900), NOW, true);

        assertThat(tracking.getLastError()).hasSize(DeliveryTracking.MAX_ERROR_LENGTH);
        assertThat(tracking.getLastError()).endsWith("...");
    }

    @Test
    void recordErrorOnAFreshDocumentMarksItPending() {
        DeliveryTracking tracking = new DeliveryTracking();

        tracking.recordError("timeout", NOW, true);

        assertThat(tracking.getState()).isEqualTo(DeliveryTrackingState.PENDING);
    }

    @Test
    void recordErrorKeepsAnExistingState() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setState(DeliveryTrackingState.UNSUPPORTED);

        tracking.recordError("timeout", NOW, true);

        assertThat(tracking.getState()).isEqualTo(DeliveryTrackingState.UNSUPPORTED);
    }

    @Test
    void isExhaustedOnceTheStreakReachesTheThreshold() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setConsecutiveErrors(4);

        assertThat(tracking.isExhausted(5)).isFalse();

        tracking.setConsecutiveErrors(5);

        assertThat(tracking.isExhausted(5)).isTrue();
    }

    @Test
    void scheduleNextMarksAFreshDocumentPending() {
        DeliveryTracking tracking = new DeliveryTracking();

        tracking.scheduleNext(NOW.plusMinutes(30));

        assertThat(tracking.getState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(tracking.getNextCheckAt()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    void scheduleNextKeepsAnExistingState() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setState(DeliveryTrackingState.PENDING);

        tracking.scheduleNext(NOW.plusHours(2));

        assertThat(tracking.getState()).isEqualTo(DeliveryTrackingState.PENDING);
        assertThat(tracking.getNextCheckAt()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    void finishStopsThePolling() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.scheduleNext(NOW.plusMinutes(30));

        tracking.finish(DeliveryTrackingState.COMPLETED);

        assertThat(tracking.getState()).isEqualTo(DeliveryTrackingState.COMPLETED);
        assertThat(tracking.getNextCheckAt()).isNull();
    }

    @Test
    void finishWithAMessageStoresTheAbbreviatedReason() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.scheduleNext(NOW.plusMinutes(30));

        tracking.finish(DeliveryTrackingState.SHIPPED_WITHOUT_DATA, "y".repeat(900));

        assertThat(tracking.getState()).isEqualTo(DeliveryTrackingState.SHIPPED_WITHOUT_DATA);
        assertThat(tracking.getNextCheckAt()).isNull();
        assertThat(tracking.getLastError()).hasSize(DeliveryTracking.MAX_ERROR_LENGTH);
    }

    @Test
    void isDueWhenNeverScheduled() {
        assertThat(new DeliveryTracking().isDue(NOW)).isTrue();
    }

    @Test
    void isDueWhenTheNextCheckIsInThePast() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setNextCheckAt(NOW.minusMinutes(1));

        assertThat(tracking.isDue(NOW)).isTrue();
    }

    @Test
    void isDueWhenTheNextCheckIsExactlyNow() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setNextCheckAt(NOW);

        assertThat(tracking.isDue(NOW)).isTrue();
    }

    @Test
    void isNotDueWhenTheNextCheckIsInTheFuture() {
        DeliveryTracking tracking = new DeliveryTracking();
        tracking.setNextCheckAt(NOW.plusMinutes(1));

        assertThat(tracking.isDue(NOW)).isFalse();
    }
}
