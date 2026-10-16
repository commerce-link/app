package pl.commercelink.marketplace;

import java.util.List;

public record MarketplaceExportOfferSearch(
        List<Hit> hits,
        int searchedRuns,
        int unreadableRuns,
        String oldestSearchedRunId) {

    public record Hit(MarketplaceExportRunHeader run, MarketplaceOfferSnapshot offer) {
    }
}
