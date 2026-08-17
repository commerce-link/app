package pl.commercelink.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceOfferSnapshot(
        String pimId,
        long price,
        long quantity,
        int removalAttempts,
        boolean pendingRemoval,
        String quantityZeroedReason) {

    public static MarketplaceOfferSnapshot published(String pimId, long price, long quantity, String quantityZeroedReason) {
        return new MarketplaceOfferSnapshot(pimId, price, quantity, 0, false, quantityZeroedReason);
    }

    public static MarketplaceOfferSnapshot pendingRemoval(String pimId, long price, int removalAttempts) {
        return new MarketplaceOfferSnapshot(pimId, price, 0L, removalAttempts, true, null);
    }
}
