package pl.commercelink.receipts;

/** Why an attempt was blocked before anything was sent. Names are persisted; message key receipts.blocked.NAME. */
public enum ReceiptBlockReason {
    NO_LINES,
    NEGATIVE_LINE,
    UNKNOWN_VAT,
    TOTAL_MISMATCH,
    EMPTY_NAME,
    MISSING_EMAIL,
    NO_PAYMENT,
    MIXED_PAYMENTS,
    MEDIUM_UNSUPPORTED,
    INVALID_REQUEST,
    NOT_ELIGIBLE,
    PROVIDER_UNAVAILABLE;

    public String messageKey() {
        return "receipts.blocked." + name();
    }
}
