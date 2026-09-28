package pl.commercelink.receipts;

/** Result of turning an order into a receipt request. */
public sealed interface ReceiptConversion {

    record Converted(ReceiptRequestSnapshot snapshot) implements ReceiptConversion {
    }

    /** Nothing can be sent; {@code detail} names the offending line or value for the operator. */
    record Blocked(ReceiptBlockReason reason, String detail) implements ReceiptConversion {
    }
}
