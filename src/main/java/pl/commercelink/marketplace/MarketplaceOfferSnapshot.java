package pl.commercelink.marketplace;

public record MarketplaceOfferSnapshot(
        String pimId,
        long price,
        long quantity,
        int removalAttempts,
        String outcome,
        String reasonCode,
        String message) {

    public static final String OUTCOME_PUBLISHED = "PUBLISHED";
    public static final String OUTCOME_REMOVAL_PENDING = "REMOVAL_PENDING";
    public static final String OUTCOME_REJECTED = "REJECTED";
    public static final String OUTCOME_EXPORT_ABORTED = "EXPORT_ABORTED";

    public static MarketplaceOfferSnapshot published(String pimId, long price, long quantity) {
        return new MarketplaceOfferSnapshot(pimId, price, quantity, 0, OUTCOME_PUBLISHED, null, null);
    }

    public static MarketplaceOfferSnapshot removalPending(String pimId, long price, int removalAttempts) {
        return new MarketplaceOfferSnapshot(pimId, price, 0L, removalAttempts, OUTCOME_REMOVAL_PENDING, null, null);
    }

    public static MarketplaceOfferSnapshot rejectedWithoutOffer(String pimId, String reasonCode, String message) {
        return new MarketplaceOfferSnapshot(pimId, 0L, 0L, 0, OUTCOME_REJECTED, reasonCode, message);
    }

    public static MarketplaceOfferSnapshot exportAborted(String message) {
        return new MarketplaceOfferSnapshot("", 0L, 0L, 0, OUTCOME_EXPORT_ABORTED, null, message);
    }

    public MarketplaceOfferSnapshot rejected(String reasonCode, String message) {
        return new MarketplaceOfferSnapshot(
                pimId, price, quantity, removalAttempts, OUTCOME_REJECTED, reasonCode, message);
    }
}
