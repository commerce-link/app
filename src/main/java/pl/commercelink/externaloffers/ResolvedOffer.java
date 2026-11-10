package pl.commercelink.externaloffers;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;

import java.util.List;

/**
 * @param ean       unified ({@link OfferEans#unify}), null when the product has none
 * @param otherEans further unified EANs of the same goods (a link listing several), tried by fulfilment after
 *                  {@code ean}; empty otherwise
 */
public record ResolvedOffer(String pimId, String ean, String mfn, String category, Source source,
                            List<String> otherEans) {

    /** The separator fulfilment splits an item's sku on: each part is a code of the same goods. */
    private static final String ALTERNATIVE_CODES = ":";

    public ResolvedOffer {
        otherEans = otherEans == null ? List.of() : List.copyOf(otherEans);
    }

    public ResolvedOffer(String pimId, String ean, String mfn, String category, Source source) {
        this(pimId, ean, mfn, category, source, List.of());
    }

    /**
     * A link's unified EANs, in the marketplace's order: the first is the item's EAN, the rest its alternatives.
     *
     * @param category the store's category name for the goods, or null to leave the item's own
     */
    static ResolvedOffer linked(List<String> eans, String category) {
        return new ResolvedOffer(null, eans.get(0), null, category, Source.LINK, eans.subList(1, eans.size()));
    }

    // persisted by name on OrderItem.externalOfferSource: do not rename the values
    public enum Source {
        /**
         * An explicit link from the seller offer key to EANs: the EANs and, where the taxonomy knows the goods, their
         * category; no pimId or manufacturer code (fulfilment fills the code from the offer it picks).
         */
        LINK,
        /** The seller offer key is our own pimId, published by CommerceLink. */
        OWN_OFFER,
        /**
         * The manufacturer code from the order line matched a PIM MPN. Lines are no longer recognised that way; only
         * items imported before carry it, and linking an offer still re-fills them.
         */
        MFN
    }

    /**
     * The order item's sku: the EAN, which fulfilment looks goods up by, followed by the other EANs of the same goods
     * joined with ":" (fulfilment tries each and takes the cheapest offer, as for an item given several codes with
     * "Assign SKU"). Only a product without an EAN falls back to its manufacturer code; null without either.
     */
    public String sku() {
        if (StringUtils.isNotBlank(ean)) {
            return otherEans.isEmpty() ? ean : ean + ALTERNATIVE_CODES + String.join(ALTERNATIVE_CODES, otherEans);
        }
        return StringUtils.isNotBlank(mfn) ? UnifiedProductIdentifiers.unifyMfn(mfn) : null;
    }

    /**
     * Gives an item exactly what a fresh import of a linked offer gives a line: its codes, its category (uncategorised
     * when the taxonomy knows none) and no manufacturer code, replacing whatever an earlier link or fulfilment left.
     * Used when the operator links or re-links an offer, where {@link #fill} would keep the old values.
     */
    public void replace(OrderItem item) {
        item.setSku(sku());
        item.setEan(StringUtils.trimToNull(ean));
        item.setManufacturerCode(StringUtils.trimToNull(mfn));
        item.setCategory(StringUtils.isNotBlank(category) ? category : Categories.UNCATEGORIZED);
        item.setExternalOfferSource(source);
    }

    /**
     * Fills a marketplace order item with the product, the same way on import and on an operator's link, so a linked
     * item looks exactly like one the next import would bring. externalItemId stays the marketplace's key (returns
     * match on it); a missing value leaves the item's own.
     */
    public void fill(OrderItem item) {
        item.setExternalOfferSource(source);
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
