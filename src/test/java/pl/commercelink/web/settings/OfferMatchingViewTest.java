package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferMatchingViewTest {

    private static OfferMatchingView view(List<Row> linked, List<Row> notInCatalog, List<Row> toReview) {
        return OfferMatchingView.of(new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 5, linked,
                notInCatalog, toReview, 0, 0));
    }

    private static OfferMatchingView notInCatalog(List<Row> rows) {
        return view(List.of(), rows, List.of());
    }

    private static Row row(String key, List<String> eans, boolean active) {
        return new Row(key, "Title " + key, eans, null, null, active, null);
    }

    @Test
    void groupsTheOffersByWhatTheCatalogSays() {
        // when
        OfferMatchingView view = view(List.of(), List.of(), List.of());

        // then
        assertThat(view.groups()).extracting(OfferMatchingView.Group::name)
                .containsExactly("linked", "notInCatalog", "toReview");
    }

    @Test
    void showsTheCatalogProductOfALinkedOffer() {
        // given
        List<Row> linked = List.of(new Row("a", "A", List.of("111"), "Klocki Classic", null));

        // when
        OfferMatchingView.Group group = view(linked, List.of(), List.of()).group("linked");

        // then
        assertThat(group.rows()).extracting(OfferMatchingView.Row::product).containsExactly("Klocki Classic");
        assertThat(group.eans()).isNull();
    }

    @Test
    void copiesOneEanPerOfferAndDropsDuplicates() {
        // given two offers share their first EAN, the first also lists a second one
        List<Row> rows = List.of(row("a", List.of("111", "222"), true), row("b", List.of("111"), true),
                row("c", List.of("333"), true));

        // when
        String eans = notInCatalog(rows).group("notInCatalog").eans();

        // then
        assertThat(eans).isEqualTo("111\n333");
    }

    @Test
    void copiesTheCatalogEanOfARowWhenItHasOne() {
        // given a manual link whose EAN is not the offer's, and a row of an older file without a catalog EAN
        List<Row> rows = List.of(new Row("a", "A", List.of("111", "222"), null, OfferMatchResult.ALREADY_LINKED, true, "999"),
                new Row("b", "B", List.of("333"), null, null, true, null));

        // when
        OfferMatchingView.Group group = notInCatalog(rows).group("notInCatalog");

        // then
        assertThat(group.eans()).isEqualTo("999\n333");
        assertThat(group.rows()).extracting(OfferMatchingView.Row::catalogEan, OfferMatchingView.Row::linkedBefore)
                .containsExactly(org.assertj.core.api.Assertions.tuple("999", true),
                        org.assertj.core.api.Assertions.tuple(null, false));
    }

    @Test
    void namesEveryReasonAnOfferIsLeftToReviewFor() {
        // given
        List<Row> rows = List.of(new Row("a", "A", List.of("111"), null, OfferMatchResult.AMBIGUOUS),
                new Row("b", "B", List.of("222"), null, OfferMatchResult.NO_MFN),
                new Row("e", "E", List.of("444"), null, OfferMatchResult.DUPLICATED_EAN),
                new Row("c", "C", List.of(), null, OfferMatchResult.NO_EAN),
                new Row("d", "D", List.of("333"), null, "stale-link"));

        // when
        OfferMatchingView.Group group = view(List.of(), List.of(), rows).group("toReview");

        // then a reason of an older file has no sentence any more
        assertThat(group.rows()).extracting(OfferMatchingView.Row::reasonKey).containsExactly(
                "store.marketplaces.offer.matching.reason.ambiguous",
                "store.marketplaces.offer.matching.reason.no-mfn",
                "store.marketplaces.offer.matching.reason.duplicated-ean",
                "store.marketplaces.offer.matching.reason.no-ean",
                null);
    }

    @Test
    void keepsInactiveOffersOutOfTheCopyListButStillListsThem() {
        // given
        List<Row> rows = List.of(row("old", List.of("111"), false), row("new", List.of("222"), true));

        // when
        OfferMatchingView.Group group = notInCatalog(rows).group("notInCatalog");

        // then
        assertThat(group.eans()).isEqualTo("222");
        assertThat(group.count()).isEqualTo(2);
        assertThat(group.rows()).extracting(OfferMatchingView.Row::sellerOfferKey).containsExactly("new", "old");
        assertThat(group.rows()).extracting(OfferMatchingView.Row::active).containsExactly(true, false);
    }

    @Test
    void offersNothingToCopyWhenEveryOfferIsInactive() {
        // when
        OfferMatchingView.Group group = notInCatalog(List.of(row("old", List.of("111"), false))).group("notInCatalog");

        // then
        assertThat(group.eans()).isEmpty();
    }
}
