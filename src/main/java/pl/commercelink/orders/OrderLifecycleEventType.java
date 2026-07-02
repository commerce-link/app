package pl.commercelink.orders;

public enum OrderLifecycleEventType {
    OrderAccepted,
    OrderCancelled,
    OrderCompleted,
    ShipmentCreated,
    InvoiceCreated,
    @Deprecated
    StatusChange
}
