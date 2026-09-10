package pl.commercelink.orders.rma;

/**
 * Constant names are persisted verbatim in {@link MarketplaceDecision#getType()}, so renaming one breaks
 * the resend of every decision already recorded.
 */
public enum ReturnLifecycleEventType {
    /** The app accepted a marketplace return: refund the buyer. */
    ReturnAccepted,
    /** The app rejected a marketplace return: send the reason to the buyer. */
    ReturnRejected
}
