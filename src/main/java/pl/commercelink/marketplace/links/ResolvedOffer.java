package pl.commercelink.marketplace.links;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

/**
 * @param ean unified ({@link OfferEans#unify}), null when the product has none
 */
public record ResolvedOffer(String pimId, String ean, String mfn, String category, Source source) {

    public enum Source {
        /**
         * An explicit link from the seller offer key to EANs, resolved to a product of the store's catalogs;
         * {@code pimId} is null when that product has none.
         */
        LINK,
        /** The seller offer key is our own pimId, published by CommerceLink. */
        OWN_OFFER,
        /** The manufacturer code from the order line matched a PIM MPN. */
        MFN
    }

    /**
     * The order item's sku: the EAN, which fulfilment looks goods up by. Only a product without an EAN falls back to
     * its manufacturer code, which fulfilment still looks up while items keyed by it remain; null without either.
     */
    public String sku() {
        if (StringUtils.isNotBlank(ean)) {
            return ean;
        }
        return StringUtils.isNotBlank(mfn) ? UnifiedProductIdentifiers.unifyMfn(mfn) : null;
    }

    /**
     * Fills a marketplace order item with the product, the same way on import and on an operator's link, so a linked
     * item looks exactly like one the next import would bring. externalItemId stays the marketplace's key (returns
     * match on it); a missing value leaves the item's own.
     */
    public void fill(OrderItem item) {
        if (sku() != null) {
            item.setSku(sku());
        }
        if (StringUtils.isNotBlank(ean)) {
            item.setEan(ean);
        }
        if (StringUtils.isNotBlank(mfn)) {
            item.setManufacturerCode(mfn);
        }
        if (StringUtils.isNotBlank(category)) {
            item.setCategory(category);
        }
    }
}
