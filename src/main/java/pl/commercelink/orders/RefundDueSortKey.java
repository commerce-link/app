package pl.commercelink.orders;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The sort key of an order in StoreIdRefundDueIndex: when it was cancelled, so the refunds waiting longest come first.
 * Only an order awaiting a refund ({@link Order#awaitsRefund()}) has one, so the index holds just those orders and the
 * orders list and the Payments page find them without reading every cancelled order. Recomputed on every save, so a
 * recorded refund takes the order out of the index by itself.
 */
public final class RefundDueSortKey {

    // an order with neither date (possible in old records) sorts as the oldest
    static final String UNDATED = "0000-01-01T00:00:00";
    // the text the dates themselves are stored in (LocalDateTime.toString() drops zero seconds), so the key can be
    // bounded by a day the way orderedAt is
    private static final DateTimeFormatter STORED_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private RefundDueSortKey() {
    }

    /**
     * Null for an order that awaits no refund: without the attribute the order is not in the index at all. An order
     * cancelled before cancelledAt was recorded is keyed by its order date instead of being given a date afterwards:
     * that date is earlier than every cancellation recorded since, so such orders still come first. Once none of them
     * awaits a refund the order date can go from here.
     */
    public static String of(Order order) {
        if (!order.awaitsRefund()) {
            return null;
        }
        LocalDateTime since = order.getCancelledAt() != null ? order.getCancelledAt() : order.getOrderedAt();
        return since == null ? UNDATED : since.format(STORED_FORMAT);
    }
}
