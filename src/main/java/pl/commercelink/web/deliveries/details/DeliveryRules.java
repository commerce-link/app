package pl.commercelink.web.deliveries.details;

import pl.commercelink.inventory.deliveries.Allocation;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.ActionState;
import pl.commercelink.web.orders.Money;

/**
 * Who may do what on a delivery, with the reason when not now. The conditions are the controller's own guards
 * (DeliveriesController), so the page never offers what the server refuses (inventory §3, spec §4.2–§6.3).
 */
public final class DeliveryRules {

    static final String ORDERING = "deliveries.details.reason.ordering";
    static final String AWAITING = "deliveries.details.reason.awaitingApproval";
    static final String RECEIVED = "deliveries.details.reason.received";
    static final String DOCUMENTS = "deliveries.details.reason.documents";
    static final String ALL_RECEIVED = "deliveries.details.reason.allReceived";
    static final String NO_TARGETS = "deliveries.details.reason.noMergeTargets";
    static final String NOT_CONFIRMED = "deliveries.details.reason.orderNotConfirmed";
    static final String REMOVE_ITEMS_FIRST = "deliveries.details.reason.removeItemsFirst";

    private DeliveryRules() {
    }

    /**
     * The gross of a net amount at the delivery's VAT, formatted, or null while the VAT is unset (tax below 1.0:
     * deliveries created by a purchase start at 0.0), so the page prints "—" instead of a gross of zero.
     */
    public static String grossOrNull(Delivery delivery, double net) {
        return delivery.getTax() >= 1.0 ? Money.format(Price.fromNet(net, delivery.getTax()).grossValue()) : null;
    }

    /** A GLOBAL delivery is bought with the platform's account, so only the super admin repeats or confirms it. */
    public static boolean purchaseActor(DeliveryViewer viewer, Delivery delivery) {
        return delivery.getConnectionMode() == ConnectionMode.GLOBAL ? viewer.superAdmin() : viewer.storeAdmin();
    }

    /** Any document (an invoice too) ends the purchase repairs, as the old page did (side task 13). */
    public static boolean purchaseRepairable(DeliveryViewer viewer, Delivery delivery) {
        return !delivery.hasBeenReceived() && delivery.getDocuments().isEmpty() && purchaseActor(viewer, delivery);
    }

    public static boolean headerEditable(DeliveryViewer viewer, Delivery delivery) {
        return viewer.manages() && !delivery.hasBeenReceived() && (!delivery.isAwaitingApproval() || viewer.superAdmin());
    }

    public static boolean allocationsEditable(DeliveryViewer viewer, Delivery delivery) {
        return !delivery.hasBeenReceived() && (!delivery.isAwaitingApproval() || viewer.superAdmin());
    }

    public static boolean hasPendingAllocations(Delivery delivery) {
        return delivery.getAllocations().stream().anyMatch(Allocation::isInAllocation);
    }

    public static ActionState receive(DeliveryViewer viewer, Delivery delivery) {
        if (delivery.isDropship() || viewer.superAdmin()) {
            return ActionState.hidden();
        }
        if (delivery.isOrderPending()) {
            return ActionState.off(ORDERING);
        }
        return delivery.isAwaitingApproval() ? ActionState.off(AWAITING) : ActionState.on();
    }

    public static ActionState ship(DeliveryViewer viewer, Delivery delivery) {
        if (!delivery.isDropship() || !viewer.manages()) {
            return ActionState.hidden();
        }
        if (delivery.getOrderStatus() == null) {
            return ActionState.on();
        }
        if (delivery.isOrderPending()) {
            return ActionState.off(ORDERING);
        }
        return ActionState.off(delivery.isAwaitingApproval() ? AWAITING : NOT_CONFIRMED);
    }

    public static ActionState merge(DeliveryViewer viewer, Delivery delivery, boolean hasTargets) {
        if (delivery.isDropship() || !viewer.manages()) {
            return ActionState.hidden();
        }
        if (delivery.isOrderPending() || delivery.isOrderDispatched()) {
            return ActionState.off(ORDERING);
        }
        return hasTargets ? ActionState.on() : ActionState.off(NO_TARGETS);
    }

    public static ActionState split(DeliveryViewer viewer, Delivery delivery) {
        if (delivery.isDropship() || !viewer.manages()) {
            return ActionState.hidden();
        }
        return delivery.isOrderPending() || delivery.isOrderDispatched() ? ActionState.off(ORDERING) : ActionState.on();
    }

    /** Removing is the way out of a purchase that ended badly: blocked only while it is genuinely in flight. */
    public static ActionState remove(DeliveryViewer viewer, Delivery delivery) {
        if (!viewer.manages()) {
            return ActionState.hidden();
        }
        boolean inFlight = delivery.isOrderPending() || (delivery.isOrderDispatched() && !delivery.isOrderOutcomeUnknown());
        return inFlight ? ActionState.off(ORDERING) : ActionState.on();
    }

    /** A super admin has nothing to do while ordering, so the checkboxes would only promise actions. */
    public static boolean selectable(DeliveryViewer viewer, Delivery delivery) {
        boolean anyAction = receive(viewer, delivery).visible() || ship(viewer, delivery).visible()
                || split(viewer, delivery).visible() || remove(viewer, delivery).visible();
        return allocationsEditable(viewer, delivery) && hasPendingAllocations(delivery) && anyAction
                && !(viewer.superAdmin() && delivery.isOrderPending());
    }

    public static ActionState changeQty(DeliveryViewer viewer, Delivery delivery, int receivedQty, int orderedQty) {
        if (!viewer.manages() || delivery.isDropship()) {
            return ActionState.hidden();
        }
        if (!delivery.getDocuments().isEmpty()) {
            return ActionState.off(DOCUMENTS);
        }
        if (delivery.hasBeenReceived() || receivedQty >= orderedQty) {
            return ActionState.off(ALL_RECEIVED);
        }
        if (delivery.isOrderPending()) {
            return ActionState.off(ORDERING);
        }
        return delivery.isAwaitingApproval() && !viewer.superAdmin() ? ActionState.off(AWAITING) : ActionState.on();
    }

    public static ActionState refresh(DeliveryViewer viewer, Delivery delivery) {
        if (!delivery.isExternalDeliveryIdProvisional() || !viewer.manages()) {
            return ActionState.hidden();
        }
        return delivery.hasBeenReceived() ? ActionState.off(RECEIVED) : ActionState.on();
    }

    public static ActionState reject(DeliveryViewer viewer, Delivery delivery) {
        return delivery.isAwaitingApproval() && viewer.superAdmin() ? ActionState.on() : ActionState.hidden();
    }

    public static ActionState delete(DeliveryViewer viewer, Delivery delivery) {
        if (!viewer.manages()) {
            return ActionState.hidden();
        }
        String reason = deleteReasonKey(delivery);
        return reason == null ? ActionState.on() : ActionState.off(reason);
    }

    /**
     * Why the delivery cannot be deleted now, or null. The purchase states come first, in the order the server refuses
     * them: while they last the items cannot be removed either, so "remove the items first" would send the operator
     * to an action he does not have.
     */
    public static String deleteReasonKey(Delivery delivery) {
        if (delivery.isAwaitingApproval()) {
            return AWAITING;
        }
        if (delivery.isOrderPending() || delivery.isOrderDispatched()) {
            return ORDERING;
        }
        return delivery.getAllocations().isEmpty() ? null : REMOVE_ITEMS_FIRST;
    }

    /** A dropship tracking state worth a warning: only while the shipment is still expected (spec §12.1 p. 22). */
    public static boolean trackingIs(Delivery delivery, DeliveryTrackingState state) {
        return delivery.isDropship() && delivery.getOrderStatus() == null && !delivery.hasBeenReceived()
                && delivery.getTrackingView().effectiveState() == state;
    }
}
