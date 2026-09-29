package pl.commercelink.stores;

/**
 * What a point-of-sale order gets as its receipt while e-receipts are on. The automation never guesses: a POS order
 * gets an automatic e-receipt only when the operator chose it for a real customer e-mail (see ReceiptEligibility),
 * because the shop may have printed the sale on its own cash register.
 */
public enum PosReceiptMode {
    /** The shop prints the receipt on its own cash register and records its number on the order. */
    CASH_REGISTER,
    /** An e-receipt sent to the customer's own e-mail. */
    E_RECEIPT,
    /** The operator chooses on every sale; the cash register is preselected. */
    ASK
}
