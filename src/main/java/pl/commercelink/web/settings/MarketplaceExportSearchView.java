package pl.commercelink.web.settings;

import pl.commercelink.marketplace.MarketplaceExportOfferSearch;
import pl.commercelink.marketplace.MarketplaceExportRunHeader;
import pl.commercelink.marketplace.MarketplaceExportRunId;

import java.util.List;

public record MarketplaceExportSearchView(String pimId, List<Hit> hits, int searchedRuns, int unreadableRuns,
                                          String searchedFrom, boolean limitReached) {

    public record Hit(MarketplaceExportRunHeader run, String runTimestamp, MarketplaceExportRunView.Row offer) {
    }

    public static MarketplaceExportSearchView of(String pimId, MarketplaceExportOfferSearch search, int hitLimit) {
        List<Hit> hits = search.hits().stream()
                .map(hit -> new Hit(hit.run(), MarketplaceExportRunId.readable(hit.run().runId()),
                        MarketplaceExportRunView.Row.of(hit.offer())))
                .toList();
        String searchedFrom = search.oldestSearchedRunId() == null
                ? null
                : MarketplaceExportRunId.readable(search.oldestSearchedRunId());
        return new MarketplaceExportSearchView(pimId, hits, search.searchedRuns(), search.unreadableRuns(),
                searchedFrom, hits.size() >= hitLimit);
    }
}
