package pl.commercelink.orders.rma;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The sort key of an RMA in StoreIdRmaListSortKeyIndex (spec §5.1): the prefix says whether the RMA is still being
 * handled, the rest orders it by creation and makes the key unique. Recomputed on every save, so a status change moves
 * the RMA between the two parts of the list by itself.
 */
public final class RmaListSortKey {

    public static final String ACTIVE = "ACTIVE#";
    public static final String CLOSED = "CLOSED#";
    /** Above every character a key holds: prefix + day + TOP reaches past every time of that day. */
    public static final String TOP = "￿";
    // an RMA without a creation date (never written by the app, but possible in old records) sorts as the oldest
    static final String UNDATED = "0000-01-01T00:00";

    private RmaListSortKey() {
    }

    public static String of(RMA rma) {
        return prefix(rma.getStatus()) + position(rma.getCreatedAt(), rma.getRmaId());
    }

    public static String prefix(RMAStatus status) {
        return status != null && status.isClosed() ? CLOSED : ACTIVE;
    }

    public static String position(LocalDateTime createdAt, String rmaId) {
        return createdAtText(createdAt) + "#" + rmaId;
    }

    public static String createdAtText(LocalDateTime createdAt) {
        return createdAt == null ? UNDATED : createdAt.toString();
    }

    public static String lowerBound(String prefix, LocalDate from) {
        return from == null ? prefix : prefix + from;
    }

    public static String upperBound(String prefix, LocalDate to) {
        return prefix + (to == null ? "" : to.toString()) + TOP;
    }
}
