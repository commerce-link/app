package pl.commercelink.shipping;

/** What a cancel request ended with, so the operator is told what actually happened. */
public enum ShipmentCancelOutcome {
    /** A new command was sent (or may have been sent); its result is read in the background. */
    REQUESTED,
    /** The result of the earlier, unconfirmed command is read again instead of sending a new one. */
    RECHECKING,
    /** The provider confirmed the cancellation right away and the shipment was cleared. */
    CANCELLED,
    /** The provider refused the cancellation right away; the reason is in the result. */
    FAILED,
    /** The order or its package was no longer there on the fresh read: nothing was sent. */
    GONE
}
