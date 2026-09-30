package pl.commercelink.receipts;

/**
 * Why the order's edits are locked for its e-receipt ({@link ReceiptOrderState#receiptLock}): the lock is the same,
 * only its wording differs.
 * <ul>
 *     <li>ISSUING: the attempt has no outcome yet ("Trwa wystawianie e-paragonu").</li>
 *     <li>ATTACHING: the receipt is fiscalised (or closed by hand) and its document is on its way to the order ("E-paragon
 *     jest zafiskalizowany, trwa dołączanie go do zamówienia"); once it is attached the order is invoiced and the
 *     invoiced locks take over.</li>
 *     <li>ATTACH_FAILED: fiscalised, but attaching it keeps failing (the row shows the EFFECTS_FAILED problem): the lock
 *     will not clear by itself, so its reasons send the operator to the row's advice instead of asking them to wait.</li>
 * </ul>
 */
public enum ReceiptLock {
    NONE(null), ISSUING("receipt"), ATTACHING("receiptAttaching"), ATTACH_FAILED("receiptAttachFailed");

    private static final String ISSUING_SEGMENT = ".receipt";
    private static final String SHORT = ".short";

    private final String segment;

    ReceiptLock(String segment) {
        this.segment = segment;
    }

    public boolean locks() {
        return this != NONE;
    }

    /** ISSUING for a caller that only knows whether the order is locked. */
    public static ReceiptLock of(boolean locked) {
        return locked ? ISSUING : NONE;
    }

    /**
     * A reason key worded for ISSUING in this lock's wording. The key must end with the segment {@code .receipt},
     * optionally followed by {@code .short} ({@code order.items.add.locked.receipt},
     * {@code order.bulk.unavailable.receipt.short}); only that last segment is swapped
     * ({@code order.items.add.locked.receiptAttaching}). NONE and ISSUING return the key unchanged.
     *
     * @throws IllegalArgumentException for a key that does not follow that shape
     */
    public String key(String issuingKey) {
        String tail = issuingKey.endsWith(ISSUING_SEGMENT + SHORT) ? SHORT : "";
        String base = issuingKey.substring(0, issuingKey.length() - tail.length());
        if (!base.endsWith(ISSUING_SEGMENT)) {
            throw new IllegalArgumentException("Not an e-receipt lock key: " + issuingKey);
        }
        if (this == NONE || this == ISSUING) {
            return issuingKey;
        }
        return base.substring(0, base.length() - ISSUING_SEGMENT.length()) + "." + segment + tail;
    }
}
