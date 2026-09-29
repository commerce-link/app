package pl.commercelink.receipts;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** The e-receipt section of the order screen: every attempt, newest first, and whether a new one may be issued. */
public record ReceiptOrderView(List<Row> rows, boolean canReissue) {

    /**
     * problem is the newest attempt's problem as the order page words it (cause, action, provider details), null
     * without one; attemptNo, receiptNumber and fiscalisedAt are the attempt's own; outcome is the short reason a dead attempt
     * fiscalised nothing (blocked reason or the provider's refusal), shown for earlier attempts, whose problem is
     * cleared once a newer attempt supersedes them; settled is a dead attempt whose order got its sale document another
     * way (a receipt from the shop's cash register, an invoice), so it needs nothing and shows no problem.
     */
    public record Row(String key, ReceiptAttemptState state, String statusKey, String statusTone, String documentUrl,
                      ReceiptPageProblem problem, Instant emailSentAt, Instant emailSkippedAt, boolean canCheck, boolean canClose,
                      boolean canResendEmail, int attemptNo, String receiptNumber, Instant fiscalisedAt,
                      String outcome, boolean settled) {
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** orderSettled: the order's dead attempts need nothing any more ({@link ReceiptTrigger#settlesDeadAttempts}). */
    public static ReceiptOrderView of(List<ReceiptAttempt> attempts, boolean orderQualifies, boolean orderSettled,
                                      ReceiptAlerts alerts, Instant now, Locale locale) {
        int maxAttemptNo = attempts.stream().mapToInt(ReceiptAttempt::getAttemptNo).max().orElse(0);
        List<Row> rows = attempts.stream()
                .sorted((a, b) -> Integer.compare(b.getAttemptNo(), a.getAttemptNo()))
                .map(a -> {
                    ReceiptAttention attention = ReceiptAttentionEvaluator.evaluate(a, now);
                    // A dead attempt superseded by a newer one no longer needs the operator's attention: only the
                    // newest attempt of the order still shows a problem.
                    boolean superseded = a.getState().isDead() && a.getAttemptNo() < maxAttemptNo;
                    // the bell alert of such an attempt is resolved as well (ReceiptAttemptService#reconcileDeadAttemptAlerts)
                    boolean settled = a.getState().isDead() && orderSettled;
                    ReceiptPageProblem problem = attention == null || superseded || settled ? null
                            : alerts.pageProblem(a, attention, locale);
                    return new Row(a.getReceiptKey(), a.getState(), "receipts.state." + a.getState().name(),
                            settled ? "is-neutral" : tone(a.getState()), a.getDocumentUrl(), problem, a.getEmailSentAt(),
                            a.getEmailSkippedAt(),
                            a.isScheduled() && !a.isLeasedAt(now),
                            (a.getState() == ReceiptAttemptState.ISSUING || a.getState() == ReceiptAttemptState.PENDING)
                                    && !a.isLeasedAt(now),
                            a.emailFailed() && !a.isLeasedAt(now),
                            a.getAttemptNo(), a.getReceiptNumber(), a.getFiscalisedAt(),
                            a.getState().isDead() ? alerts.outcome(a, locale) : null, settled);
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
