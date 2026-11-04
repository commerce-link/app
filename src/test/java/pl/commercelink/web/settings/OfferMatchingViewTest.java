package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferMatchingViewTest {

    private static OfferMatchingView view(List<Row> linked, List<Row> toReview) {
        return OfferMatchingView.of(new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 5, linked, toReview,
                0, 0));
    }

    private static Row row(String key, boolean active) {
        return new Row(key, "Title " + key, List.of("5900000000000"), null, active);
    }

    @Test
    void groupsTheOffersIntoLinkedAndToReview() {
        // when
        OfferMatchingView view = view(List.of(row("a", true)), List.of());

        // then
        assertThat(view.groups()).extracting(OfferMatchingView.Group::name).containsExactly("linked", "toReview");
        assertThat(view.groups()).extracting(OfferMatchingView.Group::count).containsExactly(1, 0);
    }

    @Test
    void namesEveryReasonAnOfferIsLeftToReviewFor() {
        // given
        List<Row> rows = List.of(new Row("c", "C", List.of(), OfferMatchResult.NO_EAN, true),
                new Row("k", "K", List.of("111"), OfferMatchResult.NO_KEY, true),
                new Row("a", "A", List.of("222"), "ambiguous", true),
                new Row("b", "B", List.of("333"), "no-mfn", true));

        // when
        OfferMatchingView.Group group = view(List.of(), rows).group("toReview");

        // then a reason of an older file (the catalog and manufacturer code ones) has no sentence any more
        assertThat(group.rows()).extracting(OfferMatchingView.Row::reasonKey).containsExactly(
                "store.marketplaces.offer.matching.reason.no-ean",
                "store.marketplaces.offer.matching.reason.no-key",
                null, null);
    }

    @Test
    void listsActiveOffersFirstKeepingTheirOrder() {
        // given
        List<Row> rows = List.of(row("old", false), row("a", true), row("b", true));

        // when
        OfferMatchingView.Group group = view(rows, List.of()).group("linked");

        // then
        assertThat(group.rows()).extracting(OfferMatchingView.Row::sellerOfferKey, OfferMatchingView.Row::active)
                .containsExactly(org.assertj.core.api.Assertions.tuple("a", true),
                        org.assertj.core.api.Assertions.tuple("b", true),
                        org.assertj.core.api.Assertions.tuple("old", false));
    }
}
