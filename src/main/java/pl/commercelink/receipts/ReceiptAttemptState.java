package pl.commercelink.receipts;

/**
 * App-side state of one receipt attempt. Constant NAMES are persisted — never rename one.
 * Dead attempts certainly fiscalised nothing, so only after them may a new key be issued.
 */
public enum ReceiptAttemptState {
    /** issue must be called (again) with the same key: before the first call, or after an unknown outcome. */
    ISSUING,
    /** The provider accepted the receipt; poll until it is fiscalised or failed. */
    PENDING,
    /** Registered in fiscal memory (terminal); the link, document and e-mail may still follow. */
    FISCALISED,
    /** The provider failed or refused the receipt (terminal, dead). */
    FAILED,
    /** Data error found before anything was sent (terminal, dead). */
    BLOCKED,
    /** An operator closed a hung attempt with a document they resolved at the provider; no more polling. */
    CLOSED_MANUALLY;

    public boolean isDead() {
        return this == FAILED || this == BLOCKED;
    }

    public boolean isLive() {
        return this == ISSUING || this == PENDING || this == FISCALISED;
    }
}
