package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptScheduleTest {

    private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

    @Test
    void issueRetriesBackOffToAnHourAndNeverStop() {
        assertThat(ReceiptSchedule.nextIssue(1, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(ReceiptSchedule.nextIssue(4, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(ReceiptSchedule.nextIssue(6, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(ReceiptSchedule.nextIssue(50, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    }

    @Test
    void pendingIsPolledOftenFirstThenQuarterHourlyThenHourly() {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setCreatedAt(NOW.minus(Duration.ofMinutes(3)));
        attempt.setPollCount(0);
        assertThat(ReceiptSchedule.nextPending(attempt, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        attempt.setPollCount(10);
        assertThat(ReceiptSchedule.nextPending(attempt, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        attempt.setCreatedAt(NOW.minus(Duration.ofHours(25)));
        assertThat(ReceiptSchedule.nextPending(attempt, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
    }

    @Test
    void theLinkIsAwaitedForSevenDays() {
        ReceiptAttempt attempt = new ReceiptAttempt();
        attempt.setFiscalisedAt(NOW.minus(Duration.ofMinutes(1)));
        attempt.setPollCount(0);
        assertThat(ReceiptSchedule.nextLink(attempt, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        attempt.setPollCount(8);
        assertThat(ReceiptSchedule.nextLink(attempt, NOW)).isEqualTo(NOW.plus(Duration.ofHours(6)));
        attempt.setFiscalisedAt(NOW.minus(Duration.ofDays(7)).minusSeconds(1));
        assertThat(ReceiptSchedule.nextLink(attempt, NOW)).isNull();
    }
}
