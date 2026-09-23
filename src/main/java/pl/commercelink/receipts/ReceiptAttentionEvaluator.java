package pl.commercelink.receipts;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The operator-facing problem of an attempt at a moment, if any. */
public final class ReceiptAttentionEvaluator {

    static final int ISSUE_CALLS_BEFORE_ALERT = 6;
    static final Duration PENDING_ALERT_AFTER = Duration.ofHours(48);
    static final Duration MONTH_END_MIN_AGE = Duration.ofHours(1);
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private ReceiptAttentionEvaluator() {
    }

    public static ReceiptAttention evaluate(ReceiptAttempt attempt, Instant now) {
        return switch (attempt.getState()) {
            case ISSUING -> attempt.isInvalidAfterSend() ? ReceiptAttention.INVALID_AFTER_SEND
                    : attempt.getIssueCalls() >= ISSUE_CALLS_BEFORE_ALERT ? ReceiptAttention.ISSUING_UNKNOWN : null;
            case PENDING -> pending(attempt, now);
            case FISCALISED -> attempt.getEmailClaimedAt() != null && attempt.getEmailSentAt() == null
                    ? ReceiptAttention.EMAIL_NOT_SENT
                    : attempt.getLinkGaveUpAt() != null ? ReceiptAttention.LINK_MISSING : null;
            case FAILED -> ReceiptAttention.FAILED;
            case BLOCKED -> ReceiptAttention.BLOCKED;
            case CLOSED_MANUALLY -> null;
        };
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
