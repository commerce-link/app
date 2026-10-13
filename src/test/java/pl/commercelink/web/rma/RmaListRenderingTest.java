package pl.commercelink.web.rma;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import pl.commercelink.web.orders.ArchivePager;
import pl.commercelink.web.orders.OrderArchivePage.Action;
import pl.commercelink.web.orders.OrderArchivePage.Chip;
import pl.commercelink.web.orders.OrderArchivePage.DateMenu;
import pl.commercelink.web.orders.OrderArchivePage.EmptyState;
import pl.commercelink.web.orders.OrderArchivePage.Preset;
import pl.commercelink.web.orders.OrderArchivePage.SearchScope;
import pl.commercelink.web.rma.RmaListPage.*;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the RMA list with a real model and the Polish bundle. */
class RmaListRenderingTest {

    private static final String RESULTS = "<div th:replace=\"~{rma :: results}\"></div>";
    private static final String HEADER = "<div th:replace=\"~{rma :: header}\"></div>";
    private static final RmaListQuery Q = RmaListQuery.parse(new LinkedMultiValueMap<>());

    private static RmaRow row() {
        return new RmaRow("7f3c22a1", "7f3c22a1-0000", "/dashboard/rma/7f3c22a1-0000", "Allegro", "ZW-4K7Q", "Tomasz Zieliński",
                "Wrocław", "tomasz@x.pl", "91af3018", "91af3018-0000", "Oczekiwanie na przedmioty", "is-info",
                "InPost · w drodze", "Allegro: W drodze", "12.10.2026", "19 dni", "is-warn", "389,99 zł");
    }

    private static RmaListPage page(List<RmaRow> rows, EmptyState empty, SearchScope scope, List<Chip> chips, List<Tile> tiles) {
        return page(Q, rows, empty, scope, chips, tiles);
    }

    private static RmaListPage page(RmaListQuery query, List<RmaRow> rows, EmptyState empty, SearchScope scope, List<Chip> chips, List<Tile> tiles) {
        return new RmaListPage(query, tiles,
                List.of(new SegmentLink("Aktywne", 8L, "/dashboard/rma", true),
                        new SegmentLink("Zakończone", null, "/dashboard/rma?scope=closed", false)),
                new StatusMenu("wszystkie", List.of(new StatusOption("New", "Nowy", 1L, false),
                        new StatusOption("ItemsReceived", "Przedmioty otrzymane", 2L, true))),
                new DateMenu("cała historia", List.of(new Preset("Cała historia", "/dashboard/rma", true),
                        new Preset("Ostatnie 30 dni", "/dashboard/rma?period=30d", false)), null, null),
                chips, rows.isEmpty() ? "" : "RMA: 1", rows,
                new ArchivePager(0, rows.size(), null, null, rows.isEmpty() ? null : 1L), empty, scope);
    }

    private static List<Tile> tiles() {
        return List.of(new Tile("Do rozpatrzenia", "nowe zgłoszenia", 1, "/dashboard/rma?status=New", false),
                new Tile("Do sprawdzenia", "przedmioty dotarły", 2, "/dashboard/rma", true));
    }

    @Test
    void rowHasOneLinkAndEveryPieceOfTheMockup() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), null, null, List.of(), tiles())));

        // then
        assertThat(html).contains("class=\"cl-table is-orders is-rma\"")
                .contains("<a class=\"cl-row-link cl-table-code\" href=\"/dashboard/rma/7f3c22a1-0000\" title=\"7f3c22a1-0000\">7f3c22a1</a>")
                .contains("ZW-4K7Q").contains("Tomasz Zieliński").contains("cl-cell-email cl-cards-hide")
                .contains("91af3018").contains("cl-status is-info").contains("InPost · w drodze").contains("Allegro: W drodze")
                .contains("cl-due-note is-warn").contains("19 dni").contains("389,99 zł")
                .contains("aria-sort=\"descending\"").contains("data-cl-list-fragment=\"/dashboard/rma/list\"")
                .doesNotContain("href=\"/dashboard/orders/").doesNotContain("??").doesNotContain("style=");
    }

    @Test
    void tilesSegmentsAndMenusAreThere() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), null, null, List.of(), tiles())));

        // then
        assertThat(html).contains("class=\"cl-stat-grid is-orders\"").contains("Do sprawdzenia").contains("(aktywny filtr)")
                .containsPattern("<a class=\"cl-segment\" href=\"/dashboard/rma\"[^>]*aria-current=\"page\"")
                .contains("cl-segment-count").contains("data-cl-filter-menu=\"status\"").contains("data-cl-filter-menu=\"dates\"")
                .contains("placeholder=\"Numer, e-mail, nazwisko albo firma\"").contains("cl-date-presets")
                .containsPattern("value=\"ItemsReceived\"[^>]*checked");
    }

    @Test
    void theSearchFormDoesNotCarryTheScopeButTheOtherMenusDo() {
        // given
        RmaListQuery closed = RmaListQuery.parse(new LinkedMultiValueMap<>(Map.of("scope", List.of("closed"))));

        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(closed, List.of(row()), null, null, List.of(), tiles())));

        // then
        String search = html.substring(html.lastIndexOf("<form", html.indexOf("name=\"q\"")), html.indexOf("</form>", html.indexOf("name=\"q\"")));
        assertThat(search).doesNotContain("name=\"scope\"");
        assertThat(html.split("name=\"scope\"", -1).length - 1).isGreaterThanOrEqualTo(2);
    }

    @Test
    void anEmptyStoreHasNoTilesAndOffersTheFirstRma() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(),
                new EmptyState("Nie ma jeszcze żadnych zgłoszeń.", List.of(new Action("Utwórz pierwsze RMA", "/dashboard/rma/new"))),
                null, List.of(), List.of())));

        // then
        assertThat(html).doesNotContain("cl-stat-grid").doesNotContain("<table").contains("cl-list-empty")
                .contains("href=\"/dashboard/rma/new\"");
    }

    @Test
    void anExhaustedBatchOffersOlderOnes() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(), null,
                new SearchScope("Przeszukano zakończone zgłoszenia utworzone od 02.03.2025.", "Szukaj w starszych", "/dashboard/rma?q=x&after=c"),
                List.of(new Chip("Szukane: x", "/dashboard/rma", "Wyczyść: Szukane: x")), tiles())));

        // then
        assertThat(html).contains("cl-search-scope").contains("Szukaj w starszych ›").contains("cl-filter-chip")
                .contains("Wyczyść filtry");
    }

    @Test
    void headerHasTheTwoActionsAndTheGuide() {
        // when
        String html = SettingsTemplateRenderer.render(HEADER, Map.of("page", page(List.of(), null, null, List.of(), tiles())));

        // then
        assertThat(html).contains("<h1 class=\"cl-page-title\">RMA</h1>").contains("href=\"/dashboard/item/history\"")
                .contains("href=\"/dashboard/rma/new\"").contains("cl-button is-primary");
    }

    @Test
    void theTemplateUsesOnlyTheDesignSystem() throws Exception {
        // given
        String template = Files.readString(Path.of("src/main/resources/templates/rma.html"));

        // then
        assertThat(template).contains("pagination :: pages(${page.pagination()})").contains("/js/list-page.js")
                .doesNotContain("style=").doesNotContain("onclick=").doesNotContain("class=\"box\"").doesNotContain("class=\"tag")
                .doesNotContain("class=\"table").doesNotContain("class=\"button").doesNotContain("th:utext")
                .doesNotContain("<script th:inline").doesNotContain("pagination :: pagination(");
    }

    @Test
    void aSearchTextIsEscapedInTheFieldChipScopeLineAndEmptyState() {
        // given
        String attack = "<script>alert(1)</script>\"'><img src=x onerror=alert(2)>";
        RmaListQuery query = RmaListQuery.parse(new LinkedMultiValueMap<>(Map.of("q", List.of(attack))));
        RmaListPage withScope = page(query, List.of(), null, new SearchScope("Brak RMA dla „" + attack + "”.", "Szukaj w starszych",
                query.href()), List.of(new Chip("Szukane: " + attack, "/dashboard/rma", "Wyczyść: " + attack)), tiles());
        RmaListPage withEmpty = page(query, List.of(), new EmptyState("Brak RMA dla „" + attack + "”.", List.of()), null, List.of(), tiles());

        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", withScope))
                + SettingsTemplateRenderer.render(RESULTS, Map.of("page", withEmpty));

        // then
        assertThat(html).doesNotContain("<script>alert(1)").doesNotContain("<img src=x")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
