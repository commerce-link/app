package pl.commercelink.receipts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** When an attempt is looked at next. */
public final class ReceiptSchedule {

    public static final Duration LINK_WINDOW = Duration.ofDays(7);

    private static final List<Duration> ISSUE_BACKOFF = minutes(1, 2, 5, 10, 15, 30);
    private static final Duration ISSUE_LATER = Duration.ofMinutes(60);
    private static final List<Duration> PENDING_BACKOFF = minutes(1, 2, 5, 15);
    private static final Duration PENDING_FIRST_DAY = Duration.ofMinutes(15);
    private static final Duration PENDING_LATER = Duration.ofMinutes(60);
    private static final List<Duration> LINK_BACKOFF = minutes(5, 15, 30, 60);
    private static final Duration LINK_LATER = Duration.ofHours(6);

    private ReceiptSchedule() {
    }

    /** After the {@code issueCalls}-th issue call ended without a result. */
    public static Instant nextIssue(int issueCalls, Instant now) {
        return now.plus(step(ISSUE_BACKOFF, Math.max(issueCalls - 1, 0), ISSUE_LATER));
    }

    public static Instant nextPending(ReceiptAttempt attempt, Instant now) {
        boolean firstDay = attempt.getCreatedAt() == null
                || Duration.between(attempt.getCreatedAt(), now).compareTo(Duration.ofDays(1)) < 0;
        return now.plus(step(PENDING_BACKOFF, attempt.getPollCount(), firstDay ? PENDING_FIRST_DAY : PENDING_LATER));
    }

    /** Null when the link is no longer awaited. */
    public static Instant nextLink(ReceiptAttempt attempt, Instant now) {
        if (attempt.getFiscalisedAt() != null && now.isAfter(attempt.getFiscalisedAt().plus(LINK_WINDOW))) {
            return null;
        }
        return now.plus(step(LINK_BACKOFF, attempt.getPollCount(), LINK_LATER));
    }

    private static Duration step(List<Duration> backoff, int index, Duration later) {
        return index < backoff.size() ? backoff.get(index) : later;
    }

    private static List<Duration> minutes(long... values) {
        return java.util.Arrays.stream(values).mapToObj(Duration::ofMinutes).toList();
    }
}
