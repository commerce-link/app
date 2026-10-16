package pl.commercelink.web.offers;

import pl.commercelink.web.orders.AddressBlock;
import pl.commercelink.web.orders.ItemMargin;

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

    /** market null = a service; market.missing() = a product no enabled supplier offers; market.unavailable() = no answer from the index. */
    public record Row(String name, String category, String mfn, long qty, String unitPrice, boolean service,
                      ItemMargin margin, Market market) {
    }

    /** unavailable = the supplier index could not answer, which says nothing about the assortment. */
    public record Market(boolean missing, boolean unavailable, String low, String median, int suppliers, long stock) {
        public Market(boolean missing, String low, String median, int suppliers, long stock) {
            this(missing, false, low, median, suppliers, stock);
        }

        static Market none() {
            return new Market(true, null, null, 0, 0);
        }

        static Market indexDown() {
            return new Market(false, true, null, null, 0, 0);
        }
    }

    public enum DeliveryState { CHOSEN, NONE, UNKNOWN }

    public enum EstimateState { OK, NO_OFFERS, UNAVAILABLE, NO_PRODUCTS }

    public record Summary(String items, DeliveryState deliveryState, String deliveryName, String delivery, String total,
                          String net, String vat, Estimate estimate) {
    }

    /** missing = products without supplier offers, left out of the cost. */
    public record Estimate(EstimateState state, String cost, String profit, String profitNet, String margin, int missing) {
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
