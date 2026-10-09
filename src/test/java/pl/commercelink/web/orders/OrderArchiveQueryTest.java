package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.ArchiveCursor;
import pl.commercelink.orders.OrderStatus;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderArchiveQueryTest {

    private static MultiValueMap<String, String> params(String... pairs) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.add(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void bareAddressIsEveryClosedOrderOfTheWholeHistory() {
        // when
        OrderArchiveQuery q = OrderArchiveQuery.parse(params());

        // then
        assertThat(q.statuses()).containsExactlyInAnyOrder(OrderStatus.Completed, OrderStatus.Cancelled);
        assertThat(q.isFiltered()).isFalse();
        assertThat(q.href()).isEqualTo("/dashboard/orders/archive");
    }

    @Test
    void stateRoundTripsThroughTheAddress() {
        // given
        ArchiveCursor cursor = new ArchiveCursor("2026-05-03T12:00", "abc");
        OrderArchiveQuery q = OrderArchiveQuery.parse(params("status", "Cancelled", "q", "Jan Kowalski",
                "from", "2026-05-01", "to", "2026-05-31")).after(cursor, 25);

        // when
        String href = q.href();

        // then
        assertThat(href).isEqualTo("/dashboard/orders/archive?status=Cancelled&q=Jan+Kowalski&from=2026-05-01&to=2026-05-31&after="
                + cursor.encode() + "&offset=25");
        LinkedMultiValueMap<String, String> back = new LinkedMultiValueMap<>();
        org.springframework.web.util.UriComponentsBuilder.fromUriString(href).build().getQueryParams()
                .forEach((k, v) -> v.forEach(value -> back.add(k, java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(OrderArchiveQuery.parse(back)).isEqualTo(q);
    }

    @Test
    void openStatusesAndGarbageAreIgnored() {
        // when
        OrderArchiveQuery q = OrderArchiveQuery.parse(params("status", "New", "from", "yesterday", "offset", "-5", "period", "7d"));

        // then
        assertThat(q.status()).isNull();
        assertThat(q.from()).isNull();
        assertThat(q.period()).isNull();
        assertThat(q.offset()).isZero();
    }

    @Test
    void garbageCursorIsIgnored() {
        // when
        OrderArchiveQuery q = OrderArchiveQuery.parse(params("after", "not-a-cursor!", "offset", "25"));

        // then
        assertThat(q.after()).isNull();
        assertThat(q.offset()).isZero();
    }

    @Test
    void datesInTheWrongOrderAreSwappedAndOverridePeriod() {
        // when
        OrderArchiveQuery q = OrderArchiveQuery.parse(params("from", "2026-05-31", "to", "2026-05-01", "period", "30d"));

        // then
        assertThat(q.from()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(q.to()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(q.period()).isNull();
    }

    @Test
    void periodBecomesAFromDay() {
        // given
        LocalDate today = LocalDate.of(2026, 10, 9);

        // when / then
        assertThat(OrderArchiveQuery.parse(params("period", "30d")).effectiveFrom(today)).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(OrderArchiveQuery.parse(params("period", "90d")).effectiveFrom(today)).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(OrderArchiveQuery.parse(params("period", "year")).effectiveFrom(today)).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void searchModeFollowsWhatWasTyped() {
        // when / then
        assertThat(OrderArchiveQuery.parse(params("q", "8C1D02")).searchMode()).isEqualTo(OrderArchiveQuery.SearchMode.NUMBER);
        assertThat(OrderArchiveQuery.parse(params("q", "8c1d02aa-0000")).searchMode()).isEqualTo(OrderArchiveQuery.SearchMode.NUMBER);
        assertThat(OrderArchiveQuery.parse(params("q", "Jan@Example.com")).searchMode()).isEqualTo(OrderArchiveQuery.SearchMode.EMAIL);
        assertThat(OrderArchiveQuery.parse(params("q", "kowalski")).searchMode()).isEqualTo(OrderArchiveQuery.SearchMode.TEXT);
        assertThat(OrderArchiveQuery.parse(params("q", "abc")).searchMode()).isEqualTo(OrderArchiveQuery.SearchMode.TEXT);
        assertThat(OrderArchiveQuery.parse(params("q", "cafe")).searchMode()).isEqualTo(OrderArchiveQuery.SearchMode.TEXT);
        assertThat(OrderArchiveQuery.parse(params()).searchMode()).isNull();
    }

    @Test
    void changingAFilterDropsTheCursor() {
        // given
        OrderArchiveQuery paged = OrderArchiveQuery.parse(params("q", "x")).after(new ArchiveCursor("2026-05-01", "a"), 25);

        // when / then
        assertThat(paged.withStatus(OrderStatus.Completed).after()).isNull();
        assertThat(paged.withQ("y").offset()).isZero();
        assertThat(paged.withPeriod(OrderArchiveQuery.Period.LAST_30).after()).isNull();
        assertThat(paged.cleared()).isEqualTo(OrderArchiveQuery.parse(params()));
    }

    @Test
    void oldClientsAddressMapsToTheArchive() {
        // when / then
        assertThat(OrderArchiveQuery.legacyHref("8c1d02aa", null, null, null)).isEqualTo("/dashboard/orders/archive?q=8c1d02aa");
        assertThat(OrderArchiveQuery.legacyHref(" ", "jan@x.pl", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .isEqualTo("/dashboard/orders/archive?q=jan%40x.pl&from=2026-05-01&to=2026-05-31");
        assertThat(OrderArchiveQuery.legacyHref(null, null, null, null)).isEqualTo("/dashboard/orders/archive");
    }

    @Test
    void longQueryIsCut() {
        // when
        OrderArchiveQuery q = OrderArchiveQuery.parse(params("q", "x".repeat(150)));

        // then
        assertThat(q.q()).hasSize(100);
        assertThat(q.statuses()).isEqualTo(Set.of(OrderStatus.Completed, OrderStatus.Cancelled));
    }

    @Test
    void searchCutAtTheLimitKeepsNoTrailingSpaceAndSurvivesARoundTrip() {
        // given
        String text = "a".repeat(99) + " " + "more";

        // when
        OrderArchiveQuery q = OrderArchiveQuery.parse(params("q", text));

        // then
        assertThat(q.q()).isEqualTo("a".repeat(99));
        assertThat(OrderArchiveQuery.parse(params("q", q.q()))).isEqualTo(q);
    }
}
