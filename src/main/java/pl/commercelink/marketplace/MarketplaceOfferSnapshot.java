package pl.commercelink.marketplace;

public record MarketplaceOfferSnapshot(
        String pimId,
        long price,
        long quantity,
        int removalAttempts,
        String outcome,
        String reasonCode,
        String message,
        String ean,
        String manufacturerCode,
        int waitingRuns) {

    public static final String OUTCOME_PUBLISHED = "PUBLISHED";
    public static final String OUTCOME_REMOVAL_PENDING = "REMOVAL_PENDING";
    public static final String OUTCOME_REMOVAL_FAILED = "REMOVAL_FAILED";
    public static final String OUTCOME_REJECTED = "REJECTED";
    public static final String OUTCOME_EXPORT_ABORTED = "EXPORT_ABORTED";

    /*
     * waitingRuns: how many runs in a row the offer was kept because a product the index copy did not know carried its
     * codes (see MarketplaceOfferExportEventListener). Zero for every other row and for run files written before it.
     */
    public MarketplaceOfferSnapshot(String pimId, long price, long quantity, int removalAttempts, String outcome,
                                    String reasonCode, String message, String ean, String manufacturerCode) {
        this(pimId, price, quantity, removalAttempts, outcome, reasonCode, message, ean, manufacturerCode, 0);
    }

    // Run files written before the identifiers were recorded have no EAN and no manufacturer code.
    public MarketplaceOfferSnapshot(String pimId, long price, long quantity, int removalAttempts,
                                    String outcome, String reasonCode, String message) {
        this(pimId, price, quantity, removalAttempts, outcome, reasonCode, message, null, null);
    }

    public static MarketplaceOfferSnapshot published(String pimId, long price, long quantity) {
        return published(pimId, price, quantity, null, null);
    }

    public static MarketplaceOfferSnapshot published(String pimId, long price, long quantity,
                                                     String ean, String manufacturerCode) {
        return new MarketplaceOfferSnapshot(
                pimId, price, quantity, 0, OUTCOME_PUBLISHED, null, null, ean, manufacturerCode);
    }

    public static MarketplaceOfferSnapshot removalPending(String pimId, long price, int removalAttempts) {
        return removalPending(pimId, price, removalAttempts, null, null);
    }

    public static MarketplaceOfferSnapshot removalPending(String pimId, long price, int removalAttempts,
                                                          String ean, String manufacturerCode) {
        return new MarketplaceOfferSnapshot(
                pimId, price, 0L, removalAttempts, OUTCOME_REMOVAL_PENDING, null, null, ean, manufacturerCode);
    }

    public static MarketplaceOfferSnapshot removalFailed(String pimId, long price, int removalAttempts,
                                                         String reasonCode, String message,
                                                         String ean, String manufacturerCode) {
        return new MarketplaceOfferSnapshot(
                pimId, price, 0L, removalAttempts, OUTCOME_REMOVAL_FAILED, reasonCode, message, ean, manufacturerCode);
    }

    public static MarketplaceOfferSnapshot rejectedWithoutOffer(String pimId, String reasonCode, String message) {
        return new MarketplaceOfferSnapshot(pimId, 0L, 0L, 0, OUTCOME_REJECTED, reasonCode, message);
    }

    public static MarketplaceOfferSnapshot exportAborted(String message) {
        return new MarketplaceOfferSnapshot("", 0L, 0L, 0, OUTCOME_EXPORT_ABORTED, null, message);
    }

    public MarketplaceOfferSnapshot rejected(String reasonCode, String message) {
        return new MarketplaceOfferSnapshot(
                pimId, price, quantity, removalAttempts, OUTCOME_REJECTED, reasonCode, message,
                ean, manufacturerCode, waitingRuns);
    }

    public MarketplaceOfferSnapshot withWaitingRuns(int runs) {
        return new MarketplaceOfferSnapshot(
                pimId, price, quantity, removalAttempts, outcome, reasonCode, message, ean, manufacturerCode, runs);
    }
}
