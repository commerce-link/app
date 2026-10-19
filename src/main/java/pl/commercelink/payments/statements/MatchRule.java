package pl.commercelink.payments.statements;

/** What tied an operation to its orders, in the order the matcher tries them. */
public enum MatchRule {
    ORDER_NUMBER,
    DOCUMENT_NUMBER,
    REFERENCE_NUMBER,
    PAYOUT,
    AMOUNT_AND_SENDER,
    AMOUNT
}
