package pl.commercelink.orders;

public enum OrderLifecycleEventType {
    OrderAccepted,
    OrderCancelled,
    OrderCompleted,
    ShipmentCreated,
    InvoiceCreated,
    /** A marketplace return was accepted in the app: refund the buyer (payload carries MarketplaceReturnAction). */
    ReturnAccepted,
    /** A marketplace return was rejected in the app (payload carries MarketplaceReturnAction.rejectionReason). */
    ReturnRejected,
    /** @deprecated Kept so in-flight SQS messages deserialize during the deploy window; remove one release after cutover. */
    @Deprecated
    StatusChange
}
