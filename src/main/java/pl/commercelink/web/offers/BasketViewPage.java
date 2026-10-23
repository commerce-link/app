package pl.commercelink.web.offers;

import pl.commercelink.web.orders.AddressBlock;

import java.util.List;

/**
 * The store basket preview (/dashboard/basket/view/{id}): read only, every text resolved. Amounts are formatted
 * without the currency; the template adds it through general.currency.amount.
 */
public record BasketViewPage(String basketId, String shortId, String backHref, String orderHref, String offerHref,
                             String client, String createdAt, String total, int itemsCount, Pill deletionPill,
                             List<Row> products, List<Row> services, Summary summary, Customer customer, Facts facts) {

    public boolean empty() {
        return itemsCount == 0;
    }

    public boolean hasClient() {
        return client != null;
    }

    /** A message key and its one argument (null when the key has none). */
    public record Pill(String key, Object arg) {
    }

    /** The basket with the shop's prices; no supplier costs or estimated margin (the client's decision, 2026-10-22). */
    public record Row(String name, String category, String mfn, long qty, String unitPrice, boolean service) {
    }

    public enum DeliveryState { CHOSEN, NONE, UNKNOWN }

    public record Summary(String items, DeliveryState deliveryState, String deliveryName, String delivery, String total,
                          String net, String vat) {
    }

    public record Customer(AddressBlock billing, AddressBlock shipping, boolean sameAddress) {
        public boolean hasBilling() {
            return BasketViewPageFactory.hasAny(billing);
        }

        public boolean hasShipping() {
            return BasketViewPageFactory.hasAny(shipping);
        }

        public boolean empty() {
            return !hasBilling() && !hasShipping();
        }
    }

    /** sourceKey, affiliate and fulfilmentKey may be null; deletion is a resolved text. */
    public record Facts(String createdAt, String sourceKey, String affiliate, String fulfilmentKey, String deletion,
                        int cleanupDays) {
    }
}
