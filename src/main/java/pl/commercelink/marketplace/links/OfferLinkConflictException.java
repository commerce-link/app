package pl.commercelink.marketplace.links;

import pl.commercelink.products.Product;

import java.util.List;

/**
 * The chosen product's EAN is carried by other catalog products with a different (or no) manufacturer code. A link
 * holds EANs only, so it could not tell the products apart and would never fill an item; nothing is saved.
 */
public class OfferLinkConflictException extends IllegalArgumentException {

    private final transient List<Product> conflicting;

    public OfferLinkConflictException(List<Product> conflicting) {
        super(MarketplaceOfferLinking.EAN_CONFLICT);
        this.conflicting = List.copyOf(conflicting);
    }

    /** The other catalog products with the chosen product's EAN, in catalog order. */
    public List<Product> conflicting() {
        return conflicting;
    }
}
