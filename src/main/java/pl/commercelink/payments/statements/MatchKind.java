package pl.commercelink.payments.statements;

/** How sure the matcher is about an incoming operation, which decides its group on the reconciliation page. */
public enum MatchKind {
    /** Found by a number in the title, for exactly what the orders still owe: ticked for booking. */
    MATCHED,
    /** A proposal the operator confirms or changes: an amount that differs, a match by amount, several candidates. */
    CHECK,
    /** A payer that settles many orders at once; the orders are picked in the payout dialog. */
    PAYOUT,
    UNMATCHED,
    /** Its number is already on a payment of an order: booked before, from this or an overlapping statement. */
    BOOKED
}
