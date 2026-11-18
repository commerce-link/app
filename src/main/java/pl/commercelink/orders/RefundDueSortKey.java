package pl.commercelink.orders;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The sort key of an order in StoreIdRefundDueIndex. Only an order awaiting a refund ({@link Order#awaitsRefund()}) has
 * one, so the index holds just those orders and the orders list and the Payments page find them without reading every
 * cancelled order. Recomputed on every save, so a recorded refund takes the order out of the index by itself.
 */
public final class RefundDueSortKey {

    // an order without an order date (possible in old records) sorts as the oldest
    static final String UNDATED = "0000-01-01T00:00:00";
    // the text orderedAt itself is stored in (LocalDateTime.toString() drops zero seconds), so the key can be bounded by
    // a day the way orderedAt is
    private static final DateTimeFormatter STORED_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private RefundDueSortKey() {
    }

    /** Null for an order that awaits no refund: without the attribute the order is not in the index at all. */
    public static String of(Order order) {
        if (!order.awaitsRefund()) {
            return null;
        }
        LocalDateTime orderedAt = order.getOrderedAt();
        return orderedAt == null ? UNDATED : orderedAt.format(STORED_FORMAT);
    }
}
