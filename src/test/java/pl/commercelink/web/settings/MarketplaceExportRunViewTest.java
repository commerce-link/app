package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.MarketplaceOfferSnapshot;
import pl.commercelink.web.settings.MarketplaceExportRunView.Outcome;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceExportRunViewTest {

    @Test
    void offersAreCountedByOutcomeAndTheRunOpensOnTheRejections() {
        // when
        MarketplaceExportRunView view = MarketplaceExportRunView.of(List.of(
                MarketplaceOfferSnapshot.published("pim-A", 100L, 3L),
                MarketplaceOfferSnapshot.published("pim-B", 100L, 3L).rejected("EAN_INVALID", "bad EAN"),
                MarketplaceOfferSnapshot.removalPending("pim-C", 50L, 2)));

        // then
        assertThat(view.count(Outcome.PUBLISHED)).isEqualTo(1);
        assertThat(view.count(Outcome.REJECTED)).isEqualTo(1);
        assertThat(view.count(Outcome.REMOVAL_PENDING)).isEqualTo(1);
        assertThat(view.defaultFilter()).isEqualTo("rejected");
        assertThat(view.rows().get(1).searchText()).isEqualTo("pim-b ean_invalid bad ean");
    }

    @Test
    void anAbortIsTheRunsMessageNotAnOffer() {
        // when
        MarketplaceExportRunView view = MarketplaceExportRunView.of(List.of(
                MarketplaceOfferSnapshot.published("pim-A", 100L, 3L),
                MarketplaceOfferSnapshot.exportAborted("java.lang.IllegalStateException: down")));

        // then
        assertThat(view.rows()).extracting(MarketplaceExportRunView.Row::pimId).containsExactly("pim-A");
        assertThat(view.abortMessage()).isEqualTo("java.lang.IllegalStateException: down");
        assertThat(view.defaultFilter()).isEqualTo("all");
    }

    @Test
    void aRowOfAnOldFileWithoutOutcomeWasPublishedAndAMissingPriceIsNotZero() {
        // when
        MarketplaceExportRunView view = MarketplaceExportRunView.of(List.of(
                new MarketplaceOfferSnapshot("pim-A", 100L, 3L, 0, "", "", ""),
                MarketplaceOfferSnapshot.rejectedWithoutOffer("pim-B", "NO_PRICE", "no price")));

        // then
        assertThat(view.rows().get(0).outcome()).isEqualTo(Outcome.PUBLISHED);
        assertThat(view.rows().get(0).reasonCode()).isNull();
        assertThat(view.rows().get(1).price()).isNull();
    }

    @Test
    void anOutcomeThisVersionDoesNotKnowIsNotShownAsARejection() {
        // when
        MarketplaceExportRunView view = MarketplaceExportRunView.of(List.of(
                new MarketplaceOfferSnapshot("pim-1", 100L, 1L, 0, "WITHHELD", null, null),
                new MarketplaceOfferSnapshot("pim-2", 100L, 1L, 0, " REJECTED ", "E1", "Za krótki tytuł")));

        // then
        assertThat(view.rows()).extracting(MarketplaceExportRunView.Row::outcome)
                .containsExactly(MarketplaceExportRunView.Outcome.UNKNOWN, MarketplaceExportRunView.Outcome.REJECTED);
    }
}
