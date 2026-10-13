package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import pl.commercelink.orders.rma.RMAStatus;
import pl.commercelink.orders.rma.RmaCursor;
import pl.commercelink.web.orders.OrderArchiveQuery.Period;
import pl.commercelink.web.rma.RmaListQuery.Scope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RmaListQueryTest {

    private static MultiValueMap<String, String> params(Map<String, List<String>> values) {
        return new LinkedMultiValueMap<>(values);
    }

    private static RmaListQuery parse(Map<String, List<String>> values) {
        return RmaListQuery.parse(params(values));
    }

    @Test
    void aBareAddressIsTheActiveListAndWritesBackBare() {
        // when
        RmaListQuery q = parse(Map.of());

        // then
        assertThat(q.effectiveScope()).isEqualTo(Scope.ACTIVE);
        assertThat(q.href()).isEqualTo("/dashboard/rma");
        assertThat(q.withScope(Scope.CLOSED).href()).isEqualTo("/dashboard/rma?scope=closed");
    }

    @Test
    void aSearchCoversBothScopesUntilOneIsChosen() {
        // when
        RmaListQuery q = parse(Map.of("q", List.of("  Kowalski  ")));

        // then
        assertThat(q.q()).isEqualTo("Kowalski");
        assertThat(q.effectiveScope()).isNull();
        assertThat(q.searchesActive()).isTrue();
        assertThat(q.searchesClosed()).isTrue();
        assertThat(q.withScope(Scope.ACTIVE).href()).isEqualTo("/dashboard/rma?scope=active&q=Kowalski");
        assertThat(q.allowedStatuses()).containsExactly(RMAStatus.values());
    }

    @Test
    void statusesOutsideTheScopeAndAFullSelectionMeanNoFilter() {
        // when
        RmaListQuery mixed = parse(Map.of("status", List.of("New", "Completed", "Nonsense")));
        RmaListQuery everyClosed = parse(Map.of("scope", List.of("closed"), "status", List.of("Completed", "Rejected")));

        // then
        assertThat(mixed.statuses()).containsExactly(RMAStatus.New);
        assertThat(mixed.href()).isEqualTo("/dashboard/rma?status=New");
        assertThat(everyClosed.statuses()).isEmpty();
        assertThat(everyClosed.accepts(RMAStatus.Rejected)).isTrue();
        assertThat(everyClosed.accepts(RMAStatus.New)).isFalse();
    }

    @Test
    void aTileSetsTheActiveScopeAndKeepsSearchAndDates() {
        // given
        RmaListQuery q = parse(Map.of("q", List.of("x"), "period", List.of("30d")));

        // when
        RmaListQuery tile = q.forTile(Set.of(RMAStatus.Approved, RMAStatus.WaitingForItems));

        // then
        assertThat(tile.href()).isEqualTo("/dashboard/rma?scope=active&status=Approved&status=WaitingForItems&q=x&period=30d");
    }

    @Test
    void datesAreSwappedAndAPeriodGivesWayToAnExplicitRange() {
        // when
        RmaListQuery q = parse(Map.of("from", List.of("2026-10-13"), "to", List.of("2026-10-01"), "period", List.of("30d")));

        // then
        assertThat(q.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(q.to()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(q.period()).isNull();
        assertThat(parse(Map.of("period", List.of("90d"))).effectiveFrom(LocalDate.of(2026, 10, 13))).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(parse(Map.of("period", List.of("year"))).period()).isEqualTo(Period.THIS_YEAR);
    }

    @Test
    void activePagesByOffsetAndClosedByCursor() {
        // given
        RmaCursor cursor = new RmaCursor("2026-10-13T09:05", "a1");

        // when
        RmaListQuery active = parse(Map.of("offset", List.of("50")));
        RmaListQuery closed = parse(Map.of("scope", List.of("closed"), "after", List.of(cursor.encode()), "offset", List.of("25")));
        RmaListQuery closedWithoutCursor = parse(Map.of("scope", List.of("closed"), "offset", List.of("25")));

        // then
        assertThat(active.offset()).isEqualTo(50);
        assertThat(active.href()).isEqualTo("/dashboard/rma?offset=50");
        assertThat(closed.after()).isEqualTo(cursor);
        assertThat(closed.offset()).isEqualTo(25);
        assertThat(closedWithoutCursor.offset()).isZero();
        assertThat(closed.withQ("x").after()).isNull();
    }

    @Test
    void aSearchPagesForwardOnly() {
        // when
        RmaListQuery q = parse(Map.of("q", List.of("x"), "before", List.of(new RmaCursor("2026-10-13T09:05", "a1").encode())));

        // then
        assertThat(q.before()).isNull();
    }

    @Test
    void clearingKeepsTheScopeOnly() {
        // given
        RmaListQuery q = parse(Map.of("scope", List.of("closed"), "status", List.of("Rejected"), "q", List.of("x"), "period", List.of("30d")));

        // when / then
        assertThat(q.cleared().href()).isEqualTo("/dashboard/rma?scope=closed");
        assertThat(q.withoutStatus(RMAStatus.Rejected).href()).isEqualTo("/dashboard/rma?scope=closed&q=x&period=30d");
        assertThat(q.activeFilterCount()).isEqualTo(2);
    }

    @Test
    void theOldSearchFormBecomesTheNewAddress() {
        // when / then
        assertThat(RmaListQuery.legacyHref(params(Map.of()))).isEmpty();
        assertThat(RmaListQuery.legacyHref(params(Map.of("email", List.of("jan@x.pl"), "createdAtStart", List.of("2026-10-01")))))
                .contains("/dashboard/rma?q=jan%40x.pl&from=2026-10-01");
        assertThat(RmaListQuery.legacyHref(params(Map.of("statuses", List.of("Rejected,Completed")))))
                .contains("/dashboard/rma?scope=closed");
        assertThat(RmaListQuery.legacyHref(params(Map.of("statuses", List.of("New,Approved")))))
                .contains("/dashboard/rma?status=New&status=Approved");
        assertThat(RmaListQuery.legacyHref(params(Map.of("rmaId", List.of(""), "orderId", List.of("o-1")))))
                .contains("/dashboard/rma?q=o-1");
    }
}
