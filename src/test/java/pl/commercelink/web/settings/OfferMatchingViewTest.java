package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.links.OfferMatchResult;
import pl.commercelink.marketplace.links.OfferMatchResult.Row;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferMatchingViewTest {

    private static OfferMatchingView view(List<Row> notInCatalog, List<Row> noPimEntry) {
        return OfferMatchingView.of(new OfferMatchResult(Instant.parse("2026-11-02T09:15:00Z"), 5, List.of(),
                notInCatalog, noPimEntry, List.of(), 0, 0));
    }

    private static Row row(String key, List<String> eans, boolean active) {
        return new Row(key, "Title " + key, eans, null, null, active, null);
    }

    @Test
    void copiesOneEanPerOfferAndDropsDuplicates() {
        // given two offers share their first EAN, the first also lists a second one
        List<Row> rows = List.of(row("a", List.of("111", "222"), true), row("b", List.of("111"), true),
                row("c", List.of("333"), true));

        // when
        String eans = view(List.of(), rows).group("noPimEntry").eans();

        // then
        assertThat(eans).isEqualTo("111\n333");
    }

    @Test
    void copiesTheLinkedEntrysPrimaryGtinForAProductMissingFromTheCatalog() {
        // given the offer's first EAN is not the entry's primary GTIN, and another offer has no catalog EAN stored
        List<Row> rows = List.of(new Row("a", "A", List.of("111", "222"), "pim-1", null, true, "222"),
                new Row("b", "B", List.of("333"), "pim-2", OfferMatchResult.ALREADY_LINKED, true, null));

        // when
        String eans = view(rows, List.of()).group("notInCatalog").eans();

        // then
        assertThat(eans).isEqualTo("222\n333");
    }

    @Test
    void keepsInactiveOffersOutOfTheCopyListButStillListsThem() {
        // given
        List<Row> rows = List.of(row("old", List.of("111"), false), row("new", List.of("222"), true));

        // when
        OfferMatchingView.Group group = view(List.of(), rows).group("noPimEntry");

        // then
        assertThat(group.eans()).isEqualTo("222");
        assertThat(group.count()).isEqualTo(2);
        assertThat(group.rows()).extracting(OfferMatchingView.Row::sellerOfferKey).containsExactly("new", "old");
        assertThat(group.rows()).extracting(OfferMatchingView.Row::active).containsExactly(true, false);
    }

    @Test
    void offersNothingToCopyWhenEveryOfferIsInactive() {
        // when
        OfferMatchingView.Group group = view(List.of(), List.of(row("old", List.of("111"), false))).group("noPimEntry");

        // then
        assertThat(group.eans()).isEmpty();
    }
}
