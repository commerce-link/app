package pl.commercelink.marketplace.links;

import pl.commercelink.products.Product;

import java.util.List;

/**
 * The chosen product's EAN is carried by other catalog products with a different (or no) manufacturer code. A link
 * holds EANs only, so it could not tell the products apart and would never fill an item; nothing is saved.
 */
public class OfferLinkConflictException extends IllegalArgumentException {

    private final transient List<Product> conflicting;

    /**
     * @param conflicting      the other products whose manufacturer code differs from the chosen one's or is missing
     * @param onlyMissingCodes true when every conflicting product just lacks a code: filling it resolves the conflict,
     *                         so the message asks for the code rather than for a fixed EAN
     */
    public OfferLinkConflictException(List<Product> conflicting, boolean onlyMissingCodes) {
        super(onlyMissingCodes ? MarketplaceOfferLinking.EAN_CONFLICT_MISSING_CODE : MarketplaceOfferLinking.EAN_CONFLICT);
        this.conflicting = List.copyOf(conflicting);
    }

    /** The other catalog products with the chosen product's EAN and another or no manufacturer code, in catalog order. */
    public List<Product> conflicting() {
        return conflicting;
    }
}
