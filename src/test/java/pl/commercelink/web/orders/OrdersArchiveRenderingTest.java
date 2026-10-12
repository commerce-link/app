package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.web.orders.OrderArchivePage.*;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the orders archive with a real model and the Polish bundle. */
class OrdersArchiveRenderingTest {

    private static final String RESULTS = "<div th:replace=\"~{orders/archive :: results}\"></div>";
    private static final String HEADER = "<div th:replace=\"~{orders/archive :: header}\"></div>";
    private static final OrderArchiveQuery Q = OrderArchiveQuery.parse(new LinkedMultiValueMap<>());

    private static OrderRow row() {
        return new OrderRow("o1", "/dashboard/orders/o1?returnTo=%2Fdashboard%2Forders%2Farchive", "8C1D02", "Sklep", null,
                "Anna Nowak", "Kraków", "anna@x.pl", "08.10.2026", "18:42", "", "Zakończone", "is-ok", "1 249,00 zł", null,
                List.of(new OrderRow.DocMark("invoice", "PAR", "is-done", "Paragon: PAR/1")));
    }

    private static OrderArchivePage page(List<OrderRow> rows, EmptyState empty, OpenNotice open, SearchScope scope, List<Chip> chips) {
        return new OrderArchivePage(Q,
                List.of(new SegmentLink("Wszystkie", "/dashboard/orders/archive", true),
                        new SegmentLink("Zakończone", "/dashboard/orders/archive?status=Completed", false),
                        new SegmentLink("Anulowane", "/dashboard/orders/archive?status=Cancelled", false)),
                new DateMenu("cała historia", List.of(new Preset("Cała historia", "/dashboard/orders/archive", true),
                        new Preset("Ostatnie 30 dni", "/dashboard/orders/archive?period=30d", false)), null, null),
                chips, rows.isEmpty() ? "" : "Zamówienia: 1–1", rows,
                new ArchivePager(0, rows.size(), null, rows.isEmpty() ? null : "/dashboard/orders/archive?after=x&offset=25", rows.isEmpty() ? null : 644L),
                empty, open, scope);
    }

    @Test
    void rowIsTheOrdersListRowWithTheOrderDay() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), null, null, null, List.of())));

        // then
        assertThat(html).contains("class=\"cl-table is-orders is-archive\"")
                .contains("<a class=\"cl-row-link\" href=\"/dashboard/orders/o1?returnTo=%2Fdashboard%2Forders%2Farchive\">8C1D02</a>")
                .contains("08.10.2026").contains("18:42").contains("cl-status is-ok").contains("cl-doc-mark is-done")
                .contains("aria-sort=\"descending\"").contains("data-cl-list-fragment=\"/dashboard/orders/archive/list\"")
                .contains("Następna").contains("1–1 z 644")
                .doesNotContain("cl-table-check").doesNotContain("??").doesNotContain("style=").doesNotContain("is-primary\" ");
    }

    @Test
    void toolbarHasSegmentsSearchAndTheDateMenuWithShortcuts() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), null, null, null, List.of())));

        // then
        assertThat(html).containsPattern("<a class=\"cl-segment\" href=\"/dashboard/orders/archive\"[^>]*aria-current=\"page\"")
                .contains("placeholder=\"Numer, e-mail, nazwisko albo firma\"")
                .contains("cl-date-presets").contains("Ostatnie 30 dni").contains("Złożone");
    }

    @Test
    void exhaustedBatchOffersOlderOrders() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(), null, null,
                new SearchScope("Przeszukano zamówienia złożone od 18.09.2026.", "Szukaj w starszych", "/dashboard/orders/archive?q=x&after=c"),
                List.of(new Chip("Szukane: x", "/dashboard/orders/archive", "Wyczyść: Szukane: x")))));

        // then
        assertThat(html).contains("cl-search-scope").contains("Przeszukano zamówienia złożone od 18.09.2026.")
                .contains("href=\"/dashboard/orders/archive?q=x&amp;after=c\"").contains("Szukaj w starszych")
                .doesNotContain("cl-list-empty");
    }

    @Test
    void openOrderNumberLeadsToTheOrder() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(),
                null, new OpenNotice("Zamówienie 8C1D02 jest otwarte.", "Otwórz zamówienie", "/dashboard/orders/o1"), null, List.of())));

        // then
        assertThat(html).contains("cl-alert is-info").contains("Zamówienie 8C1D02 jest otwarte.")
                .contains("href=\"/dashboard/orders/o1\"");
    }

    @Test
    void emptyStateListsItsActions() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(),
                new EmptyState("Brak zamówień dla „x”.", List.of(new Action("Wyczyść wyszukiwanie", "/dashboard/orders/archive"))),
                null, null, List.of())));

        // then
        assertThat(html).contains("cl-list-empty").contains("Brak zamówień dla „x”.").contains("Wyczyść wyszukiwanie")
                .doesNotContain("<table");
    }

    @Test
    void headerLeadsBackToOrders() {
        // when
        String html = SettingsTemplateRenderer.render(HEADER, Map.of());

        // then
        assertThat(html).contains("class=\"cl-back\" href=\"/dashboard/orders\"").contains("Archiwum zamówień")
                .contains("data-intro-toggle");
    }
}
