package pl.commercelink.receipts;

/**
 * Why the order's edits are locked for its e-receipt ({@link ReceiptOrderState#receiptLock}): the lock is the same,
 * only its wording differs. ISSUING: the attempt has no outcome yet ("Trwa wystawianie e-paragonu"). ATTACHING: the
 * receipt is fiscalised (or closed by hand) and its document is being attached to the order ("E-paragon jest
 * zafiskalizowany, trwa dołączanie go do zamówienia"); once it is attached the order is invoiced and the invoiced locks
 * take over.
 */
public enum ReceiptLock {
    NONE, ISSUING, ATTACHING;

    public boolean locks() {
        return this != NONE;
    }

    /** ISSUING for a caller that only knows whether the order is locked. */
    public static ReceiptLock of(boolean locked) {
        return locked ? ISSUING : NONE;
    }

    /**
     * A reason key worded for ISSUING (its last-but-one or last segment is {@code receipt}, e.g.
     * {@code order.items.add.locked.receipt}, {@code order.bulk.unavailable.receipt.short}) in this lock's wording:
     * the ATTACHING variant is the same key with {@code receiptAttaching} in place of that segment.
     */
    public String key(String issuingKey) {
        return this == ATTACHING ? issuingKey.replace(".receipt", ".receiptAttaching") : issuingKey;
    }
}
