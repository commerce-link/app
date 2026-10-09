package pl.commercelink.inventory.deliveries;

/**
 * What synchronizing a purchase invoice does to the delivery's payments. One rule for the preview and for the save,
 * so the preview never announces something else than the save does.
 */
public enum InvoicePaymentSync {

    /** The invoice is paid and the delivery records no payment: a bank transfer for the delivery's gross is added. */
    ADD,
    /** The invoice is unpaid while the delivery records payments: every payment of the delivery is removed. */
    REMOVE,
    NONE;

    public static InvoicePaymentSync of(boolean invoicePaid, boolean deliveryHasPayments) {
        if (invoicePaid && !deliveryHasPayments) {
            return ADD;
        }
        if (!invoicePaid && deliveryHasPayments) {
            return REMOVE;
        }
        return NONE;
    }
}
