package pl.commercelink.shipping;

/** Who a shipment belongs to: where its state is kept and what its creation sets off. */
public enum ShipmentOwnerType {
    ORDER,
    /** Sent by the operator from an RMA (to a repair center or back to the customer). */
    RMA,
    /** The customer's return, picked up at the customer's address; the pickup is ordered at once. */
    RMA_RETURN,
    /** Sent from the warehouse; not stored anywhere, the pickup is ordered at once. */
    WAREHOUSE
}
