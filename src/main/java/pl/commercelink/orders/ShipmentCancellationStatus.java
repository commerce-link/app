package pl.commercelink.orders;

/** Where the cancellation of a shipment's courier order stands; a confirmed cancellation clears the shipment. */
public enum ShipmentCancellationStatus {
    PENDING,
    FAILED,
    UNCONFIRMED
}
