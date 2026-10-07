package pl.commercelink.web.warehousedocuments;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.Chip;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.DateMenu;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.DocumentRow;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.EmptyState;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.Option;
import pl.commercelink.web.warehousedocuments.WarehouseDocumentListPage.SegmentLink;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the warehouse documents list with a real model and the Polish bundle. */
class WarehouseDocumentsListRenderingTest {

    private static final String PATH = "/dashboard/warehouse-documents";
    private static final String RESULTS = "<div th:replace=\"~{warehouse-documents :: results}\"></div>";
    private static final String DISABLED = "<div th:replace=\"~{warehouse-documents :: disabled}\"></div>";

    private static DocumentRow row() {
        return new DocumentRow(PATH + "/details?documentId=d1", "PZ/MAG1/2026/000214", "Przyjęcie zewnętrzne", true,
                "Dostawa od dostawcy", null, "Dostawa 3f2a9c1e", "AB S.A.", "07.10.2026", "14:32 · Jan Kowalski", null);
    }

    private static WarehouseDocumentListPage page(List<DocumentRow> rows, List<Chip> chips, EmptyState empty, boolean superAdmin) {
        WarehouseDocumentListQuery q = new WarehouseDocumentListQuery(PATH, DocumentKind.PZ, List.of(), null, null, null, 1);
        return new WarehouseDocumentListPage(q, superAdmin, true, PATH + "/list",
                List.of(new SegmentLink("Wszystkie", null, PATH, false), new SegmentLink("PZ", "Przyjęcie zewnętrzne", PATH + "?type=PZ", true)),
                List.of(new Option("SupplierDelivery", "Dostawa od dostawcy", false)), "wszystkie",
                new DateMenu("cała historia", null, null), chips, "Dokumenty: 1–1", rows,
                Pagination.openEnded(1, 25, rows.size(), false, n -> PATH + "?page=" + n), empty, "/dashboard/store/warehouse");
    }

    @Test
    void rowLinksTheNumberAndNamesTypeReasonSourceCounterpartyAndDate() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), List.of(), null, false)));

        // then
        assertThat(html).contains("class=\"cl-table is-orders is-documents\"")
                .contains("<a class=\"cl-row-link cl-doc-no\" href=\"/dashboard/warehouse-documents/details?documentId=d1\">PZ/MAG1/2026/000214</a>")
                .contains("fa-sign-in-alt").contains("Przyjęcie zewnętrzne").contains("Dostawa od dostawcy")
                .contains("Dostawa 3f2a9c1e").contains("AB S.A.").contains("07.10.2026").contains("14:32 · Jan Kowalski")
                .contains("aria-sort=\"descending\"")
                .doesNotContain("??").doesNotContain("class=\"button").doesNotContain("class=\"table").doesNotContain("style=");
    }

    @Test
    void segmentsCarryTheFullTypeNameAndMarkTheActiveOne() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), List.of(), null, false)));

        // then
        assertThat(html).contains("data-cl-list-results").contains("data-cl-list-fragment=\"/dashboard/warehouse-documents/list\"")
                .containsPattern("<a class=\"cl-segment\" href=\"/dashboard/warehouse-documents\\?type=PZ\"\\s+data-cl-list-nav title=\"Przyjęcie zewnętrzne\" aria-current=\"page\"")
                .contains("placeholder=\"Numer dokumentu, EAN albo kod producenta\"");
    }

    @Test
    void emptyListSaysWhyAndOffersToClearFilters() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(),
                List.of(new Chip("Szukasz: „x”", PATH + "?type=PZ", "Usuń zawężenie: Szukasz: „x”")),
                new EmptyState("Brak dokumentów z „x”.", "Wyczyść filtry", PATH + "?type=PZ"), false)));

        // then
        assertThat(html).doesNotContain("is-documents").contains("Brak dokumentów z „x”.")
                .contains("class=\"cl-filter-chip\"").contains("Wyczyść filtry");
    }

    @Test
    void noticeRendersInsideTheResultsFragment() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), List.of(), null, false),
                "documentsNotice", "Dokument nie istnieje."));

        // then
        assertThat(html).contains("Dokument nie istnieje.").contains("data-cl-list-notice");
        assertThat(html.indexOf("data-cl-list-notice")).isGreaterThan(html.indexOf("data-cl-list-results"));
    }

    @Test
    void superAdminSeesTheStoreColumn() {
        // given
        DocumentRow withStore = new DocumentRow(row().href(), row().number(), row().typeName(), true, row().reason(), null,
                row().source(), row().counterparty(), row().date(), row().timeAndAuthor(), "s1");

        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(withStore), List.of(), null, true)));

        // then
        assertThat(html).contains(">Sklep<").contains(">s1<");
    }

    @Test
    void switchedOffDocumentsShowAWarningWithASettingsLink() {
        // given
        WarehouseDocumentListPage off = new WarehouseDocumentListPage(
                new WarehouseDocumentListQuery(PATH, null, List.of(), null, null, null, 1), false, false, PATH + "/list",
                List.of(), List.of(), null, null, List.of(), null, List.of(), null, null, "/dashboard/store/warehouse");

        // when
        String html = SettingsTemplateRenderer.render(DISABLED, Map.of("page", off));

        // then
        assertThat(html).contains("cl-alert is-warn").contains("Dokumenty magazynowe są wyłączone")
                .contains("href=\"/dashboard/store/warehouse\"");
    }

    @Test
    void phoneSortBarStatesTheNewestFirstOrderAboveTheTable() {
        // when
        String html = SettingsTemplateRenderer.render(RESULTS, Map.of("page", page(List.of(row()), List.of(), null, false)));

        // then
        assertThat(html).containsPattern("<nav class=\"cl-table-sortbar\"[^>]*>\\s*<span class=\"cl-eyebrow\">Od najnowszych</span>")
                .containsPattern("(?s)cl-table-sortbar.*<table");
    }

    @Test
    void documentNumberBreaksInACardAndStaysOnOneLineFromTheTableWidth() throws Exception {
        // given
        String css = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/css/commercelink.css"));

        // then
        assertThat(css).containsPattern("\\.cl-table\\.is-documents \\.cl-doc-no \\{[^}]*overflow-wrap: anywhere[^}]*\\}")
                .containsPattern("@media screen and \\(min-width: 720px\\) \\{\\s*\\.cl-page \\.cl-table\\.is-documents \\.cl-doc-no \\{ white-space: nowrap; \\}");
    }
}
