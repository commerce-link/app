package pl.commercelink.receipts;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The operator-facing problem of an attempt at a moment, if any. */
public final class ReceiptAttentionEvaluator {

    static final int ISSUE_CALLS_BEFORE_ALERT = 6;
    static final int EFFECTS_FAILURES_BEFORE_ALERT = 3;
    static final Duration PENDING_ALERT_AFTER = Duration.ofHours(48);
    static final Duration MONTH_END_MIN_AGE = Duration.ofHours(1);
    static final Duration PROVIDER_UNAVAILABLE_AFTER = Duration.ofMinutes(30);
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private ReceiptAttentionEvaluator() {
    }

    public static ReceiptAttention evaluate(ReceiptAttempt attempt, Instant now) {
        return switch (attempt.getState()) {
            case ISSUING -> attempt.isInvalidAfterSend() ? ReceiptAttention.INVALID_AFTER_SEND
                    : providerUnavailable(attempt, now) ? ReceiptAttention.PROVIDER_UNAVAILABLE
                    : attempt.getIssueCalls() >= ISSUE_CALLS_BEFORE_ALERT ? ReceiptAttention.ISSUING_UNKNOWN : null;
            case PENDING -> pending(attempt, now);
            case FISCALISED -> attempt.getEffectsFailures() >= EFFECTS_FAILURES_BEFORE_ALERT
                    ? ReceiptAttention.EFFECTS_FAILED
                    : attempt.emailFailed()
                    ? ReceiptAttention.EMAIL_NOT_SENT
                    : attempt.getLinkGaveUpAt() != null ? ReceiptAttention.LINK_MISSING : null;
            case FAILED -> ReceiptAttention.FAILED;
            case BLOCKED -> ReceiptAttention.BLOCKED;
            case CLOSED_MANUALLY -> null;
        };
    }

    /**
     * The provider was never reached even once (issueCalls still 0) and has not been for a while: an adapter that
     * is not installed, or a store configuration nobody will fix on its own, would otherwise retry silently forever.
     */
    private static boolean providerUnavailable(ReceiptAttempt attempt, Instant now) {
        if (attempt.getIssueCalls() != 0 || attempt.getLastErrorAt() == null || attempt.getCreatedAt() == null) {
            return false;
        }
        return Duration.between(attempt.getCreatedAt(), now).compareTo(PROVIDER_UNAVAILABLE_AFTER) > 0;
    }

    private static ReceiptAttention pending(ReceiptAttempt attempt, Instant now) {
        Duration age = attempt.getCreatedAt() == null ? Duration.ZERO : Duration.between(attempt.getCreatedAt(), now);
        LocalDate today = LocalDate.ofInstant(now, WARSAW);
        boolean monthEnd = today.getDayOfMonth() > today.lengthOfMonth() - 2;
        if (monthEnd && age.compareTo(MONTH_END_MIN_AGE) >= 0) {
            return ReceiptAttention.PENDING_MONTH_END;
        }
        return age.compareTo(PENDING_ALERT_AFTER) > 0 ? ReceiptAttention.PENDING_LONG : null;
    }
}
