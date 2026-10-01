package pl.commercelink.receipts;

/** Why an attempt was blocked before anything was sent. Names are persisted; message key receipts.blocked.NAME. */
public enum ReceiptBlockReason {
    NO_LINES,
    NEGATIVE_LINE,
    UNKNOWN_VAT,
    TOTAL_MISMATCH,
    EMPTY_NAME,
    MISSING_EMAIL,
    /** A point-of-sale sale without the customer's own e-mail: the shop may already have printed its receipt. */
    POS_NO_CUSTOMER_EMAIL,
    /** No longer raised (an unpaid order gets a receipt without payments); kept for attempts stored with it. */
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
