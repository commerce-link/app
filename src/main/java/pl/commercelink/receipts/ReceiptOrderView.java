package pl.commercelink.receipts;

import java.time.Instant;
import java.util.List;

/** The e-receipt section of the order screen: every attempt, newest first, and whether a new one may be issued. */
public record ReceiptOrderView(List<Row> rows, boolean canReissue) {

    public record Row(String key, ReceiptAttemptState state, String statusKey, String statusTone, String documentUrl,
                      String problem, Instant emailSentAt, Instant emailSkippedAt, boolean canCheck, boolean canClose,
                      boolean canResendEmail) {
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public static ReceiptOrderView of(List<ReceiptAttempt> attempts, boolean orderQualifies, ReceiptAlerts alerts,
                                      Instant now) {
        List<Row> rows = attempts.stream()
                .sorted((a, b) -> Integer.compare(b.getAttemptNo(), a.getAttemptNo()))
                .map(a -> {
                    ReceiptAttention attention = ReceiptAttentionEvaluator.evaluate(a, now);
                    return new Row(a.getReceiptKey(), a.getState(), "receipts.state." + a.getState().name(),
                            tone(a.getState()), a.getDocumentUrl(),
                            attention == null ? null : alerts.message(a, attention), a.getEmailSentAt(),
                            a.getEmailSkippedAt(),
                            a.isScheduled() && !a.isLeasedAt(now),
                            (a.getState() == ReceiptAttemptState.ISSUING || a.getState() == ReceiptAttemptState.PENDING)
                                    && !a.isLeasedAt(now),
                            a.emailFailed() && !a.isLeasedAt(now));
                })
                .toList();
        boolean allDead = attempts.stream().allMatch(a -> a.getState().isDead());
        return new ReceiptOrderView(rows, !attempts.isEmpty() && allDead && orderQualifies);
    }

    private static String tone(ReceiptAttemptState state) {
        return switch (state) {
            case ISSUING -> "is-info";
            case PENDING -> "is-warn";
            case FISCALISED -> "is-ok";
            case FAILED, BLOCKED -> "is-bad";
            case CLOSED_MANUALLY -> "is-neutral";
        };
    }
}
