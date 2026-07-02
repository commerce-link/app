package pl.commercelink.orders;

public enum OrderLifecycleEventType {
    OrderAccepted,
    OrderCancelled,
    OrderCompleted,
    ShipmentCreated,
    InvoiceCreated,
    /** @deprecated Kept so in-flight SQS messages deserialize during the deploy window; remove one release after cutover. */
    @Deprecated
    StatusChange
}
