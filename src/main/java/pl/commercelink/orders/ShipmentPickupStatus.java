package pl.commercelink.orders;

public enum ShipmentPickupStatus {
    /** Handed in at a carrier point (or the carrier has no pickups): nothing to order. */
    NOT_REQUIRED,
    /** Waits for "Zamów odbiór". */
    AWAITING,
    PENDING,
    ORDERED,
    /** The last order did not work; it can be ordered again. */
    FAILED
}
