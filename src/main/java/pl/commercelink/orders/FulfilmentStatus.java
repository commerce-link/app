package pl.commercelink.orders;

public enum FulfilmentStatus {
    New,
    Allocation,
    Ordered,
    Reserved,
    Delivered,
    InRMA,
    InExternalService,
    Returned,
    Replaced,
    Destroyed;
}
