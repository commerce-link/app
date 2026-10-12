package pl.commercelink.orders;

/** One entry of StoreIdOrderedAtIndex (projection: orderId, status, email, fulfilmentType) with the stored orderedAt. */
public record ArchiveIndexEntry(String orderId, String orderedAt, OrderStatus status, String email) {

    public ArchiveCursor cursor() {
        return new ArchiveCursor(orderedAt, orderId);
    }
}
