package pl.commercelink.orders;

public enum ShipmentPickupStatus {
    /** Handed in at a carrier point (or the carrier has no pickups): nothing to order. */
    NOT_REQUIRED,
    /** Waits for "Zamów odbiór". */
    AWAITING,
    PENDING,
    ORDERED,
    /** The last order did not work; it can be ordered again. */
    FAILED,
    /** The operator handed it to the carrier another way (at a point, a courier booked elsewhere): nothing to order. */
    HANDED_OVER
}
