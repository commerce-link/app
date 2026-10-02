package pl.commercelink.inventory.deliveries;

import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.time.LocalDate;

/**
 * The sort key of a delivery in StoreIdDeliveryListSortKeyIndex (spec §7.1): the prefix says which part of the deliveries list the
 * delivery belongs to, the rest orders it there. On its way: planned date (undated last). Received without a purchase
 * invoice: the settlement backlog, by reception. Received and invoiced (or the store's own warehouse, which never gets
 * one): the settled history, by reception. Recomputed on every save, so a date change, a reception or an attached
 * invoice moves the delivery by itself.
 */
public final class DeliveryListKey {

    public static final String IN_TRANSIT = "IN_TRANSIT#";
    public static final String TO_SETTLE = "TO_SETTLE#";
    public static final String SETTLED = "SETTLED#";
    public static final String UNDATED = "9999-12-31";

    private DeliveryListKey() {
    }

    public static String of(Delivery delivery) {
        if (delivery.getReceivedAt() == null) {
            LocalDate planned = delivery.getEstimatedDeliveryAt();
            return IN_TRANSIT + (planned == null ? UNDATED : planned.toString());
        }
        boolean ownWarehouse = SupplierRegistry.WAREHOUSE.equals(delivery.getProvider());
        String prefix = delivery.isInvoiced() || ownWarehouse ? SETTLED : TO_SETTLE;
        return prefix + delivery.getReceivedAt();
    }

    /** A bound of a reception window on the key: the upper one reaches past every time of that day. */
    public static String receivedBound(String prefix, LocalDate day, boolean upper) {
        return prefix + day + (upper ? "\uffff" : "");
    }
}
