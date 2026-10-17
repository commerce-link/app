package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.MarketplaceExportOfferSearch;
import pl.commercelink.marketplace.MarketplaceExportRunHeader;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceExportSearchViewTest {

    private static final String RUN_ID = "8213415334_2026-08-13_01-31-05";
    private static final int HIT_LIMIT = 10;

    @Test
    void showsEachHitAsItsRunWithAReadableTimestampAndTheOfferRow() {
        // given
        MarketplaceExportRunHeader run = new MarketplaceExportRunHeader("allegro", "catalog-1", RUN_ID, true);
        MarketplaceExportOfferSearch search = new MarketplaceExportOfferSearch(List.of(
                new MarketplaceExportOfferSearch.Hit(run, MarketplaceOfferSnapshot.published("pim-A", 3503L, 7L)
                        .rejected("MISSING_PARAMETER", "no warranty"))), 1, 0, RUN_ID);

        // when
        MarketplaceExportSearchView view = MarketplaceExportSearchView.of("pim-A", search, HIT_LIMIT);

        // then
        assertThat(view.pimId()).isEqualTo("pim-A");
        assertThat(view.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.run()).isEqualTo(run);
            assertThat(hit.runTimestamp()).isEqualTo("2026-08-13 01:31:05");
            assertThat(hit.offer().outcome()).isEqualTo(MarketplaceExportRunView.Outcome.REJECTED);
            assertThat(hit.offer().price()).isEqualTo(3503L);
            assertThat(hit.offer().reasonCode()).isEqualTo("MISSING_PARAMETER");
        });
    }

    @Test
    void namesTheOldestSearchedRunByItsReadableTimestamp() {
        // given
        MarketplaceExportOfferSearch search = new MarketplaceExportOfferSearch(List.of(), 500, 2, RUN_ID);

        // when
        MarketplaceExportSearchView view = MarketplaceExportSearchView.of("pim-A", search, HIT_LIMIT);

        // then
        assertThat(view.searchedRuns()).isEqualTo(500);
        assertThat(view.unreadableRuns()).isEqualTo(2);
        assertThat(view.searchedFrom()).isEqualTo("2026-08-13 01:31:05");
    }

    @Test
    void leavesTheOldestSearchedRunEmptyWhenNothingWasSearched() {
        // given
        MarketplaceExportOfferSearch search = new MarketplaceExportOfferSearch(List.of(), 0, 0, null);

        // when
        MarketplaceExportSearchView view = MarketplaceExportSearchView.of("pim-A", search, HIT_LIMIT);

        // then
        assertThat(view.searchedFrom()).isNull();
    }

    @Test
    void tellsThatOlderRunsWereNotSearchedOnceTheHitLimitIsReached() {
        // when
        MarketplaceExportSearchView view = MarketplaceExportSearchView.of("pim-A", searchWithHits(HIT_LIMIT), HIT_LIMIT);

        // then
        assertThat(view.limitReached()).isTrue();
    }

    @Test
    void doesNotClaimTheLimitWithFewerHits() {
        // when
        MarketplaceExportSearchView view =
                MarketplaceExportSearchView.of("pim-A", searchWithHits(HIT_LIMIT - 1), HIT_LIMIT);

        // then
        assertThat(view.limitReached()).isFalse();
    }

    private MarketplaceExportOfferSearch searchWithHits(int count) {
        List<MarketplaceExportOfferSearch.Hit> hits = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            hits.add(new MarketplaceExportOfferSearch.Hit(
                    new MarketplaceExportRunHeader("allegro", "catalog-1",
                            String.format("82134153%02d_2026-08-13_01-31-05", index), false),
                    MarketplaceOfferSnapshot.published("pim-A", 1999L, 7L)));
        }
        return new MarketplaceExportOfferSearch(hits, count, 0, hits.get(count - 1).run().runId());
    }
}
