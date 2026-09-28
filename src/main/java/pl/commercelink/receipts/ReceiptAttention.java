package pl.commercelink.receipts;

/** Why a receipt attempt needs the operator. Names are persisted on the attempt; message key receipts.attention.NAME. */
public enum ReceiptAttention {
    ISSUING_UNKNOWN,
    PROVIDER_UNAVAILABLE,
    INVALID_AFTER_SEND,
    PENDING_LONG,
    PENDING_MONTH_END,
    FAILED,
    BLOCKED,
    LINK_MISSING,
    EMAIL_NOT_SENT,
    EFFECTS_FAILED;

    public String messageKey() {
        return "receipts.attention." + name();
    }
}
