package pl.commercelink.web.deliveries.details;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListState;
import pl.commercelink.inventory.deliveries.DeliveryTrackingState;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.CardAction;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.Header;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.MoreMenu;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.PrimaryAction;
import pl.commercelink.web.deliveries.details.DeliveryPageModel.StatusCard;
import pl.commercelink.web.orders.OrderFormats;
import pl.commercelink.web.orders.OrderLabels;
import pl.commercelink.web.orders.OrderPageModelFactory;

import java.util.ArrayList;
import java.util.List;

/** The record header (spec §4) and the status cards above the items (spec §5). */
final class DeliveryHeaderFactory {

    private DeliveryHeaderFactory() {
    }

    static Header header(DeliveryPageData data, DeliveryViewer viewer, DeliveryLinks links) {
        Delivery delivery = data.delivery();
        DeliveryListState state = DeliveryListState.of(delivery);
        boolean received = delivery.hasBeenReceived();
        String externalId = delivery.isOrderPending() ? null : StringUtils.trimToNull(delivery.getExternalDeliveryId());
        String noteKey = delivery.isOrderPending() ? "deliveries.details.meta.number.pending"
                : externalId == null && delivery.isOrderDispatched() ? "deliveries.details.meta.number.dispatched" : null;
        int total = delivery.getAllocations().size();
        int done = (int) delivery.getAllocations().stream().filter(allocation -> !allocation.isInAllocation()).count();
        String dateKey = received
                ? (delivery.isDropship() ? "deliveries.details.meta.shipped" : "deliveries.details.meta.received")
                : delivery.getEstimatedDeliveryAt() == null ? null : "deliveries.details.meta.due";
        return new Header(state.messageKey(), state.tone(),
                delivery.isDropship() ? "deliveries.details.type.dropship" : "deliveries.details.type.warehouse",
                delivery.isDropship() ? "fa-truck" : "fa-warehouse",
                data.supplierName(), externalId, externalId == null ? null : OrderPageModelFactory.safeWebUrl(data.partnerSiteUrl()),
                delivery.isExternalDeliveryIdProvisional(), noteKey, OrderFormats.date(delivery.getOrderedAt()), dateKey,
                received ? OrderFormats.date(delivery.getReceivedAt()) : OrderFormats.date(delivery.getEstimatedDeliveryAt()),
                done, total, !received && done > 0 && done < total
                        ? (delivery.isDropship() ? "deliveries.details.meta.progress.shipped" : "deliveries.details.meta.progress")
                        : null,
                DeliveryRules.grossOrNull(delivery, delivery.getTotalCost()), primary(data, viewer, links),
                new MoreMenu(DeliveryRules.refresh(viewer, delivery), links.refreshOrderId(),
                        DeliveryRules.reject(viewer, delivery), links.open("reject"),
                        DeliveryRules.delete(viewer, delivery), links.confirm("delete")));
    }

    /** The first row of spec §4.2 that applies; its condition is the server's condition for the action. */
    static PrimaryAction primary(DeliveryPageData data, DeliveryViewer viewer, DeliveryLinks links) {
        Delivery delivery = data.delivery();
        if (delivery.isAwaitingApproval() && viewer.superAdmin()) {
            return new PrimaryAction("deliveries.details.primary.approve", "fa-check-circle", links.approval(), null, null);
        }
        if (delivery.isOrderFailed() && DeliveryRules.purchaseRepairable(viewer, delivery)) {
            return new PrimaryAction("deliveries.details.primary.retry", "fa-redo-alt", null, links.retry(), null);
        }
        if (delivery.isOrderDispatched() && DeliveryRules.purchaseRepairable(viewer, delivery)) {
            return new PrimaryAction("deliveries.details.primary.reconcile", "fa-search", null, links.reconcile(), null);
        }
        boolean waiting = !delivery.hasBeenReceived() && DeliveryRules.hasPendingAllocations(delivery);
        boolean receivable = delivery.getOrderStatus() == null || delivery.isOrderFailed() || delivery.isOrderDispatched();
        if (!delivery.isDropship() && waiting && receivable && !viewer.superAdmin()) {
            return new PrimaryAction("deliveries.details.primary.receiveAll", "fa-check", links.open("receive-all"), null,
                    "receive-all-dialog");
        }
        if (delivery.isDropship() && waiting && delivery.getOrderStatus() == null && viewer.manages()
                && delivery.getTrackingView().effectiveState() != DeliveryTrackingState.CANCELLED_BY_SUPPLIER) {
            // one shipment dialog: its fields sit once in the allocations form, the opener only checks every line first
            return new PrimaryAction("deliveries.details.primary.shipAll", "fa-shipping-fast", links.open("ship-all"), null,
                    "ship-dialog");
        }
        return null;
    }

    static List<StatusCard> statusCards(DeliveryPageData data, DeliveryViewer viewer, DeliveryLinks links) {
        Delivery delivery = data.delivery();
        List<StatusCard> cards = new ArrayList<>();
        if (delivery.hasBeenReceived()) {
            return cards;
        }
        boolean repair = DeliveryRules.purchaseRepairable(viewer, delivery);
        String reason = StringUtils.trimToNull(delivery.getOrderErrorMessage());
        CardAction complete = new CardAction("deliveries.details.action.complete", "complete-dialog", links.open("complete"), false, false);
        CardAction force = new CardAction("deliveries.details.action.force", "force-dialog", links.open("force"), true, false);
        if (delivery.isOrderFailed()) {
            cards.add(new StatusCard(OrderLabels.BAD, "fa-exclamation-circle", "deliveries.details.status.failed.title",
                    repair ? "deliveries.details.status.failed.text" : noRepairKey(viewer, delivery), reason,
                    repair ? List.of(complete) : List.of()));
        } else if (delivery.isOrderOutcomeUnknown()) {
            cards.add(new StatusCard(OrderLabels.WARN, "fa-exclamation-triangle", "deliveries.details.status.unknown.title",
                    "deliveries.details.status.unknown.text", reason, repair ? List.of(complete, force) : List.of()));
        } else if (delivery.isOrderDispatched()) {
            cards.add(new StatusCard(OrderLabels.WARN, "fa-exclamation-triangle", "deliveries.details.status.dispatched.title",
                    "deliveries.details.status.dispatched.text", null, repair ? List.of(complete, force) : List.of()));
        } else if (delivery.isOrderPending()) {
            cards.add(new StatusCard(OrderLabels.INFO, "fa-hourglass-half", "deliveries.details.status.pending.title",
                    "deliveries.details.status.pending.text", null, List.of()));
        } else if (delivery.isAwaitingApproval() && !viewer.superAdmin()) {
            cards.add(new StatusCard(OrderLabels.INFO, "fa-hourglass-half", "deliveries.details.status.approval.title",
                    "deliveries.details.status.approval.text", null, List.of()));
        }
        // the card tells the operator to remove the items; once they are gone there is nothing left to act on
        if (DeliveryRules.trackingIs(delivery, DeliveryTrackingState.CANCELLED_BY_SUPPLIER) && !delivery.getAllocations().isEmpty()) {
            boolean removable = DeliveryRules.remove(viewer, delivery).enabled() && DeliveryRules.hasPendingAllocations(delivery);
            cards.add(new StatusCard(OrderLabels.BAD, "fa-exclamation-circle", "deliveries.details.status.cancelled.title",
                    "deliveries.details.status.cancelled.text", null,
                    removable ? List.of(new CardAction("deliveries.details.action.removeAll", "remove-dialog",
                            links.open("remove-all"), true, true)) : List.of()));
        }
        if (DeliveryRules.trackingIs(delivery, DeliveryTrackingState.SHIPPED_WITHOUT_DATA)) {
            cards.add(new StatusCard(OrderLabels.WARN, "fa-exclamation-triangle", "deliveries.details.status.noData.title",
                    "deliveries.details.status.noData.text", null, List.of()));
        }
        if (DeliveryRules.trackingIs(delivery, DeliveryTrackingState.GIVEN_UP)) {
            cards.add(new StatusCard(OrderLabels.WARN, "fa-exclamation-triangle", "deliveries.details.status.givenUp.title",
                    "deliveries.details.status.givenUp.text", null, List.of()));
        }
        if (delivery.hasDirectToConsumerAllocations()) {
            cards.add(new StatusCard(OrderLabels.INFO, "fa-info-circle", "deliveries.details.status.dtc.title",
                    "deliveries.details.status.dtc.text", null, List.of()));
        }
        return cards;
    }

    /** Who can repeat a failed order when the viewer cannot; nothing when only a linked document stands in the way. */
    private static String noRepairKey(DeliveryViewer viewer, Delivery delivery) {
        if (DeliveryRules.purchaseActor(viewer, delivery)) {
            return null;
        }
        return delivery.getConnectionMode() == ConnectionMode.GLOBAL
                ? "deliveries.details.status.noRepair.platform" : "deliveries.details.status.noRepair.store";
    }
}
