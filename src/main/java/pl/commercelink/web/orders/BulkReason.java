package pl.commercelink.web.orders;

/**
 * Why a bulk action of the selection row is unavailable for the whole order. key is the full sentence a menu entry shows
 * under its label; shortKey is the few words "Remove" shows right before itself, where the row has room for one line.
 * Every reason carries both, so any reason the factory gives any action can be shown in either place.
 */
public enum BulkReason {

    DROPSHIP_LOCKED("order.items.action.dropship.locked", "order.items.action.dropship.locked.short"),
    SPLIT_UNAVAILABLE("order.bulk.unavailable.split", "order.bulk.unavailable.split.short"),
    RECEIPT_ISSUING("order.bulk.unavailable.receipt", "order.bulk.unavailable.receipt.short"),
    RECEIPT_ATTACHING("order.bulk.unavailable.receiptAttaching", "order.bulk.unavailable.receiptAttaching.short"),
    RECEIPT_ATTACH_FAILED("order.bulk.unavailable.receiptAttachFailed", "order.bulk.unavailable.receiptAttachFailed.short");

    private final String key;
    private final String shortKey;

    BulkReason(String key, String shortKey) {
        this.key = key;
        this.shortKey = shortKey;
    }

    public String key() { return key; }
    public String shortKey() { return shortKey; }
}
