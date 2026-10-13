package pl.commercelink.orders.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.web.rma.RmaListPage;
import pl.commercelink.web.rma.RmaListQuery;
import pl.commercelink.web.rma.RmaRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RmaListServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    RMARepository repository;
    RmaListService service;
    final List<RMA> store = new ArrayList<>();

    @BeforeEach
    void inMemoryIndex() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        service = new RmaListService(repository, messages);
        lenient().when(repository.listRange(eq("s1"), anyString(), anyString(), anyBoolean())).thenAnswer(call -> {
            String lo = call.getArgument(1);
            String hi = call.getArgument(2);
            boolean newestFirst = call.getArgument(3);
            Comparator<RMA> byKey = Comparator.comparing(RmaListSortKey::of);
            return store.stream().filter(r -> RmaListSortKey.of(r).compareTo(lo) >= 0 && RmaListSortKey.of(r).compareTo(hi) <= 0)
                    .sorted(newestFirst ? byKey.reversed() : byKey).iterator();
        });
    }

    private RMA add(String id, RMAStatus status, LocalDateTime createdAt, String email) {
        RMA rma = new RMA();
        rma.setStoreId("s1");
        rma.setRmaId(id);
        rma.setStatus(status);
        rma.setCreatedAt(createdAt);
        rma.setEmail(email);
        store.add(rma);
        return rma;
    }

    private RmaListPage page(Map<String, List<String>> params) {
        return service.page("s1", RmaListQuery.parse(new LinkedMultiValueMap<>(params)), TODAY, PL);
    }

    private static List<String> ids(RmaListPage page) {
        return page.rows().stream().map(RmaRow::rmaId).toList();
    }

    private void seedActiveAndClosed() {
        add("n1", RMAStatus.New, LocalDateTime.of(2026, 10, 13, 9, 0), "anna@x.pl");
        add("w1", RMAStatus.WaitingForItems, LocalDateTime.of(2026, 10, 12, 9, 0), "jan.kowalski@x.pl");
        add("a1", RMAStatus.Approved, LocalDateTime.of(2026, 10, 11, 9, 0), "ewa@x.pl");
        add("r1", RMAStatus.ItemsReceived, LocalDateTime.of(2026, 10, 10, 9, 0), "marek@x.pl");
        for (int i = 0; i < 60; i++) {
            add("c" + i, i % 4 == 0 ? RMAStatus.Rejected : RMAStatus.Completed, LocalDateTime.of(2026, 10, 9, 12, 0).minusDays(i),
                    i == 30 ? "jan.kowalski@x.pl" : "c" + i + "@x.pl");
        }
    }

    @Test
    void theDefaultViewIsActiveNewestFirstWithTilesAndATotal() {
        // given
        seedActiveAndClosed();

        // when
        RmaListPage page = page(Map.of());

        // then
        assertThat(ids(page)).containsExactly("n1", "w1", "a1", "r1");
        assertThat(page.tiles()).extracting(RmaListPage.Tile::label, RmaListPage.Tile::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Do rozpatrzenia", 1L),
                        org.assertj.core.groups.Tuple.tuple("Czekamy na klienta", 2L),
                        org.assertj.core.groups.Tuple.tuple("Do sprawdzenia", 1L),
                        org.assertj.core.groups.Tuple.tuple("W realizacji", 0L));
        assertThat(page.segments()).extracting(RmaListPage.SegmentLink::count, RmaListPage.SegmentLink::active)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(4L, true), org.assertj.core.groups.Tuple.tuple(null, false));
        assertThat(page.pagination().totalItems()).isEqualTo(4);
        assertThat(page.pagination().openEnded()).isFalse();
        assertThat(page.statusMenu().options()).extracting(RmaListPage.StatusOption::value)
                .containsExactly("New", "Approved", "WaitingForItems", "ItemsReceived", "Processing");
        assertThat(page.emptyState()).isNull();
    }

    @Test
    void aTileNarrowsToItsStatusesAndASecondClickClearsIt() {
        // given
        seedActiveAndClosed();

        // when
        RmaListPage page = page(Map.of("status", List.of("Approved", "WaitingForItems")));

        // then
        assertThat(ids(page)).containsExactly("w1", "a1");
        RmaListPage.Tile waiting = page.tiles().get(1);
        assertThat(waiting.active()).isTrue();
        assertThat(waiting.href()).isEqualTo("/dashboard/rma");
        assertThat(page.chips()).extracting(c -> c.label()).containsExactly("Status: Zatwierdzony", "Status: Oczekiwanie na przedmioty");
        assertThat(page.tiles().get(0).active()).isFalse();
    }

    @Test
    void closedPagesByCursorForwardAndBack() {
        // given
        seedActiveAndClosed();

        // when
        RmaListPage first = page(Map.of("scope", List.of("closed")));
        String next = first.pagination().nextHref();
        RmaListPage second = page(params(next));
        RmaListPage backToFirst = page(params(second.pagination().previousHref()));

        // then
        assertThat(ids(first)).hasSize(25).startsWith("c0", "c1");
        assertThat(first.pagination().openEnded()).isTrue();
        assertThat(first.pagination().previousHref()).isNull();
        assertThat(ids(second)).hasSize(25).startsWith("c25");
        assertThat(second.pagination().fromIndex()).isEqualTo(25);
        assertThat(ids(backToFirst)).isEqualTo(ids(first));
    }

    @Test
    void aSearchCoversBothScopesNewestFirst() {
        // given
        seedActiveAndClosed();

        // when
        RmaListPage page = page(Map.of("q", List.of("KOWALSKI")));

        // then
        assertThat(ids(page)).containsExactly("w1", "c30");
        assertThat(page.segments()).noneMatch(RmaListPage.SegmentLink::active);
        assertThat(page.resultsLine()).isEqualTo("RMA: 2");
        assertThat(page.scope()).isNull();
    }

    @Test
    void aSearchStopsAfterFiveHundredClosedAndOffersOlder() {
        // given
        for (int i = 0; i < 600; i++) {
            add("c" + i, RMAStatus.Completed, LocalDateTime.of(2026, 10, 9, 12, 0).minusHours(i), i == 550 ? "needle@x.pl" : "c@x.pl");
        }

        // when
        RmaListPage page = page(Map.of("q", List.of("needle")));
        RmaListPage more = page(params(page.scope().moreHref()));

        // then
        assertThat(page.rows()).isEmpty();
        assertThat(page.scope().text()).startsWith("Przeszukano zakończone zgłoszenia utworzone od");
        assertThat(page.emptyState()).isNull();
        assertThat(ids(more)).containsExactly("c550");
    }

    @Test
    void theEndDayOfARangeIsIncluded() {
        // given
        seedActiveAndClosed();

        // when
        RmaListPage page = page(Map.of("from", List.of("2026-10-11"), "to", List.of("2026-10-12")));

        // then
        assertThat(ids(page)).containsExactly("w1", "a1");
        assertThat(page.chips()).extracting(c -> c.label()).containsExactly("Utworzone: 11.10.2026 – 12.10.2026");
    }

    @Test
    void aCursorOutsideTheDateRangeShowsAnEmptyPageNotAnError() {
        // given
        seedActiveAndClosed();
        RmaCursor old = new RmaCursor("2026-01-01T00:00", "zz");

        // when
        RmaListPage page = page(Map.of("scope", List.of("closed"), "period", List.of("30d"), "after", List.of(old.encode()), "offset", List.of("25")));

        // then
        assertThat(page.rows()).isEmpty();
        assertThat(page.emptyState()).isNotNull();
    }

    @Test
    void aCursorOfADeletedRmaStillPages() {
        // given
        seedActiveAndClosed();
        RmaCursor gone = new RmaCursor(LocalDateTime.of(2026, 10, 9, 12, 0).minusDays(24).plusMinutes(1).toString(), "deleted");

        // when
        RmaListPage page = page(Map.of("scope", List.of("closed"), "after", List.of(gone.encode()), "offset", List.of("25")));

        // then
        assertThat(ids(page)).startsWith("c24");
    }

    @Test
    void aStoreWithoutRmasShowsNoTilesAndLeadsToCreatingOne() {
        // when
        RmaListPage page = page(Map.of());

        // then
        assertThat(page.tiles()).isEmpty();
        assertThat(page.emptyState().text()).startsWith("Nie ma jeszcze żadnych zgłoszeń");
        assertThat(page.emptyState().actions()).extracting(a -> a.href()).containsExactly("/dashboard/rma/new");
    }

    @Test
    void noActiveRmasLeadsToTheClosedOnes() {
        // given
        add("c1", RMAStatus.Completed, LocalDateTime.of(2026, 10, 1, 9, 0), "x@x.pl");

        // when
        RmaListPage page = page(Map.of());

        // then
        assertThat(page.tiles()).hasSize(4);
        assertThat(page.emptyState().text()).isEqualTo("Nie ma aktywnych zgłoszeń.");
        assertThat(page.emptyState().actions()).extracting(a -> a.href()).containsExactly("/dashboard/rma?scope=closed");
    }

    @Test
    void activePagesByOffsetWithATotal() {
        // given
        for (int i = 0; i < 30; i++) {
            add("n" + i, RMAStatus.New, LocalDateTime.of(2026, 10, 13, 9, 0).minusHours(i), "x@x.pl");
        }

        // when
        RmaListPage first = page(Map.of());
        RmaListPage second = page(params(first.pagination().nextHref()));

        // then
        assertThat(first.rows()).hasSize(25);
        assertThat(second.rows()).hasSize(5);
        assertThat(second.pagination().fromIndex()).isEqualTo(25);
        assertThat(second.pagination().totalItems()).isEqualTo(30);
        assertThat(second.pagination().previousHref()).isEqualTo("/dashboard/rma");
    }

    private static Map<String, List<String>> params(String href) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String query = href.contains("?") ? href.substring(href.indexOf('?') + 1) : "";
        for (String part : query.split("&")) {
            if (part.isEmpty()) continue;
            String[] kv = part.split("=", 2);
            out.computeIfAbsent(kv[0], k -> new ArrayList<>()).add(java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return out;
    }
}
