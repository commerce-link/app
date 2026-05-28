package pl.commercelink.orders.rma;

public enum RMAStatus {
    New,
    Approved,
    Rejected,
    WaitingForItems,
    ItemsReceived,
    Processing,
    Completed;
}
