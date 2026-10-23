package pl.commercelink.web.offers;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.baskets.OfferValidity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferListQueryTest {

    private static MultiValueMap<String, String> params(String... pairs) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void bareAddressIsTheOffersSegmentFirstPage() {
        // when
        OfferListQuery query = OfferListQuery.parse(params());

        // then
        assertThat(query.segment()).isEqualTo(OfferSegment.OFFERS);
        assertThat(query.isFiltered()).isFalse();
        assertThat(query.href()).isEqualTo("/dashboard/offers");
    }

    @Test
    void parsesEveryParameterAndWritesItBack() {
        // when
        OfferListQuery query = OfferListQuery.parse(params("segment", "offers", "q", "  Dell xps ", "validity", "expiring",
                "validity", "noExpiry", "from", "2026-09-01", "to", "2026-09-30", "page", "3"));

        // then
        assertThat(query.q()).isEqualTo("Dell xps");
        assertThat(query.validity()).containsExactly(OfferValidity.EXPIRING, OfferValidity.NO_EXPIRY);
        assertThat(query.activeFilterCount()).isEqualTo(4);
        assertThat(query.href()).isEqualTo("/dashboard/offers?q=Dell+xps&validity=expiring&validity=noExpiry"
                + "&from=2026-09-01&to=2026-09-30&page=3");
    }

    @Test
    void unknownValuesAreIgnoredSoOldBookmarksOpen() {
        // when
        OfferListQuery query = OfferListQuery.parse(params("segment", "carts", "validity", "soon", "from", "1.09.2026", "page", "x"));

        // then
        assertThat(query.segment()).isEqualTo(OfferSegment.OFFERS);
        assertThat(query.validity()).isEmpty();
        assertThat(query.from()).isNull();
        assertThat(query.page()).isEqualTo(1);
    }

    @Test
    void reversedDatesAreSwapped() {
        // when
        OfferListQuery query = OfferListQuery.parse(params("from", "2026-09-30", "to", "2026-09-01"));

        // then
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void changingSegmentKeepsSearchAndDatesDropsValidityAndPage() {
        // given
        OfferListQuery query = OfferListQuery.parse(params("q", "CAD", "validity", "expired", "from", "2026-09-01", "page", "2"));

        // when
        String href = query.withSegment(OfferSegment.TEMPLATES).href();

        // then
        assertThat(href).isEqualTo("/dashboard/offers?segment=templates&q=CAD&from=2026-09-01");
    }

    @Test
    void validityOutsideTheOffersSegmentIsDropped() {
        // when
        OfferListQuery query = OfferListQuery.parse(params("segment", "templates", "validity", "expired"));

        // then
        assertThat(query.validity()).isEmpty();
    }

    @Test
    void togglingAndClearingFiltersResetsThePage() {
        // given
        OfferListQuery query = OfferListQuery.parse(params("q", "CAD", "validity", "expired", "page", "4"));

        // when / then
        assertThat(query.toggleValidity(OfferValidity.ACTIVE).href()).isEqualTo("/dashboard/offers?q=CAD&validity=active&validity=expired");
        assertThat(query.withoutValidity(OfferValidity.EXPIRED).href()).isEqualTo("/dashboard/offers?q=CAD");
        assertThat(query.withQ(null).href()).isEqualTo("/dashboard/offers?validity=expired");
        assertThat(query.cleared().href()).isEqualTo("/dashboard/offers");
        assertThat(query.withPage(5).href()).endsWith("&page=5");
    }

    @Test
    void searchIsTrimmedAndCapped() {
        // when
        OfferListQuery query = OfferListQuery.parse(params("q", "x".repeat(150)));

        // then
        assertThat(query.q()).hasSize(OfferListQuery.MAX_Q);
        assertThat(OfferListQuery.parse(params("q", "   ")).q()).isNull();
    }

    @Test
    void recognisesAFullBasketId() {
        // when / then
        assertThat(OfferListQuery.looksLikeId("8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b")).isTrue();
        assertThat(OfferListQuery.looksLikeId("8f3a21c7")).isFalse();
        assertThat(OfferListQuery.looksLikeId("Stacje CAD")).isFalse();
    }

    @Test
    void legacySearchFormRedirectsToTheNewAddress() {
        // when / then
        assertThat(OfferListQuery.legacyRedirect(params("basketId", "8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b", "name", "", "type", "")))
                .contains("/dashboard/offers?q=8f3a21c7-1b2d-4e5f-9a0b-1c2d3e4f5a6b");
        assertThat(OfferListQuery.legacyRedirect(params("name", "CAD", "type", "OfferTemplate",
                "createdAtStart", "2026-09-01", "createdAtEnd", "", "page", "1")))
                .contains("/dashboard/offers?segment=templates&q=CAD&from=2026-09-01");
        assertThat(OfferListQuery.legacyRedirect(params("q", "CAD"))).isEmpty();
    }

    @Test
    void validityListIsInEnumOrderWithoutDuplicates() {
        // when
        OfferListQuery query = new OfferListQuery(OfferSegment.OFFERS, null,
                List.of(OfferValidity.EXPIRED, OfferValidity.ACTIVE, OfferValidity.EXPIRED), null, null, 1);

        // then
        assertThat(query.validity()).containsExactly(OfferValidity.ACTIVE, OfferValidity.EXPIRED);
    }
}
