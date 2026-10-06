package pl.commercelink.shipping;

import pl.commercelink.orders.Shipment;

/**
 * Where a shipment lives and what its creation sets off: an order, an RMA, a customer return or the warehouse. Every
 * write re-reads the owner and applies only while its shipment still waits for that very command.
 */
public interface ShipmentOwner {

    ShipmentOwnerType type();

    /** Saves the shipment waiting for the command, before the provider is called; false when the owner is gone. */
    boolean markCreating(ShipmentCreationCheckRequest request, Shipment placeholder);

    /** Records the provider's package id on the waiting shipment. */
    void recordExternalId(ShipmentCreationCheckRequest request);

    /** The provider refused the command right away: nothing was created. */
    void refused(ShipmentCreationCheckRequest request, String error);
}
