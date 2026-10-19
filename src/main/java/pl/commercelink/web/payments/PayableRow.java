package pl.commercelink.web.payments;

/** One row of the payables tab, every text resolved (spec §5.1). */
public record PayableRow(String href, String number, boolean dropship, String orderedText, String supplierLabel,
                         String externalText, String dueText, String dueNote, String dueTone, String stateLabel,
                         String stateTone, boolean invoiced, String invoiceMarkLabel, String amountText, String subText,
                         boolean paidOfLine, boolean refund, String deliveryId, String expected, PendingData pending,
                         String actionLabel) {

    /** The unsettled payment the dialog opens pre-filled with (the data-pending-* attributes of the row action). */
    public record PendingData(String source, String name, String referenceNo, String fee, String bankTransactionNo,
                              String bankTransactionDate) {
    }
}
