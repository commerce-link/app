package pl.commercelink.payments.statements;

/** What the operator should look at before booking a proposal. */
public enum MatchWarning {
    /** Less than the orders owe: they stay underpaid by the difference. */
    UNDERPAID,
    /** More than the orders owe: the last one is overpaid by the difference. */
    OVERPAID,
    /** The order waits for another kind of payment (online, cash on delivery) than a direct transfer. */
    METHOD_MISMATCH,
    /** Several orders owe exactly this amount. */
    AMBIGUOUS,
    /** The title names an order that is already paid (or closed). */
    ORDER_ALREADY_PAID,
    /** The payout's orders could not be told by their sum; the operator picks them. */
    NO_PAYOUT_PROPOSAL
}
