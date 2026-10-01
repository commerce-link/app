package pl.commercelink.web.payments;

/** One row of the receivables tab, every text resolved (spec §5.2). */
public record ReceivableRow(String href, String number, String sourceText, String clientName, String email,
                            String shipText, String shipNote, String shipTone, String methodLabel, String stateLabel,
                            String stateTone, String amountText, String subText, boolean paidOfLine, boolean refund,
                            String orderId, String expected, PayableRow.PendingData pending, String actionLabel) {
}
