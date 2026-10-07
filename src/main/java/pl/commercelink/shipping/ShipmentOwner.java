package pl.commercelink.shipping;

import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentPickup;

import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

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

    /** The owner still has a shipment waiting for this command; a check for anything else is dropped. */
    boolean awaits(ShipmentCreationCheckRequest request);

    /**
     * Replaces the placeholder of the command with the created shipments and sets off what creating it means for the
     * owner (tracking, lifecycle, ...). False, with nothing set off, when the placeholder no longer waits.
     */
    boolean succeeded(ShipmentCreationCheckRequest request, List<Shipment> created);

    /** The command did not create anything: error (the provider's words) or errorKey (our reason, a message key). */
    void failed(ShipmentCreationCheckRequest request, String error, String errorKey);

    /**
     * Changes the pickup of every row of these packages (every parcel of a package carries its externalId); returns
     * how many rows changed, 0 when nothing applied. A change that does not concern a pickup returns it unchanged.
     */
    int applyPickup(String storeId, String ownerId, Collection<String> externalIds,
                    UnaryOperator<ShipmentPickup> change);

    /**
     * What the owner does once a pickup of its package is settled (an e-mail, a notification); nothing by default.
     * Called by ShipmentPickupSettler and by the immediate pickup, which may settle without a command (no windows).
     */
    default void onPickupSettled(String storeId, String provider, PickupTarget target, ShipmentPickup result) {
    }
}
