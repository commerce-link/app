package pl.commercelink.inventory.deliveries;

public enum DeliveryTrackingState {
    PENDING,
    COMPLETED,
    UNSUPPORTED,
    SHIPPED_WITHOUT_DATA,
    CANCELLED_BY_SUPPLIER,
    GIVEN_UP
}
