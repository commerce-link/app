package pl.commercelink.marketplace;

import pl.commercelink.pricelist.AvailabilityAndPrice;

record MarketplaceOfferClassification(
        MarketplaceExportSkipReason exclusion,
        AvailabilityAndPrice availabilityAndPrice,
        long quantityToPublish,
        MarketplaceExportSkipReason quantityZeroedReason,
        MarketplaceExportThresholds thresholds) {

    static MarketplaceOfferClassification excluded(MarketplaceExportSkipReason reason) {
        return new MarketplaceOfferClassification(reason, null, 0L, null, null);
    }

    static MarketplaceOfferClassification published(AvailabilityAndPrice availabilityAndPrice, long quantityToPublish) {
        return new MarketplaceOfferClassification(null, availabilityAndPrice, quantityToPublish, null, null);
    }

    static MarketplaceOfferClassification zeroed(AvailabilityAndPrice availabilityAndPrice,
                                                 MarketplaceExportSkipReason reason,
                                                 MarketplaceExportThresholds thresholds) {
        return new MarketplaceOfferClassification(null, availabilityAndPrice, 0L, reason, thresholds);
    }

    boolean isExcluded() {
        return exclusion != null;
    }
}
