package pl.commercelink.orders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Edits of an owner's shipment list (an order's, an RMA's) shared by the creation and pickup flows. */
public final class ShipmentLists {

    private ShipmentLists() {
    }

    public static Optional<Shipment> creating(List<Shipment> shipments, String commandId) {
        return shipments.stream().filter(s -> s.isCreationPendingFor(commandId)).findFirst();
    }

    /** The placeholder of that command, PENDING or never confirmed (Shipment#isCreationUnsettledFor). */
    public static Optional<Shipment> unsettled(List<Shipment> shipments, String commandId) {
        return shipments.stream().filter(s -> s.isCreationUnsettledFor(commandId)).findFirst();
    }

    /** Puts the created shipments where the placeholder of that command was; false when it is not there any more. */
    public static boolean replaceCreating(List<Shipment> shipments, String commandId, List<Shipment> created) {
        return replace(shipments, s -> s.isCreationPendingFor(commandId), created);
    }

    /** As replaceCreating, for a placeholder PENDING or never confirmed. */
    public static boolean replaceUnsettled(List<Shipment> shipments, String commandId, List<Shipment> created) {
        return replace(shipments, s -> s.isCreationUnsettledFor(commandId), created);
    }

    private static boolean replace(List<Shipment> shipments, Predicate<Shipment> placeholder, List<Shipment> created) {
        int index = -1;
        for (int i = 0; i < shipments.size(); i++) {
            if (placeholder.test(shipments.get(i))) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        shipments.removeIf(placeholder);
        shipments.addAll(index, created);
        return true;
    }

    /** Applies the change to the pickup of every row of these packages; returns how many rows changed. */
    public static int applyPickup(List<Shipment> shipments, Collection<String> externalIds, UnaryOperator<ShipmentPickup> change) {
        int changed = 0;
        for (Shipment s : shipments) {
            if (s.getExternalId() == null || s.getPickup() == null || !externalIds.contains(s.getExternalId())) {
                continue;
            }
            ShipmentPickup next = change.apply(s.getPickup());
            // reference comparison: a change that does not concern this shipment returns the same object
            if (next != s.getPickup()) {
                s.setPickup(next);
                changed++;
            }
        }
        return changed;
    }
}
