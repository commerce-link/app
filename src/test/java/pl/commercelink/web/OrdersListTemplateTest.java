package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OrdersListTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/orders/list.html"), StandardCharsets.UTF_8);
    }

    @Test
    void oneH1AndTheSharedShell() throws Exception {
        String html = page();
        assertThat(html).contains("layout:decorate=\"~{layout}\"").contains("class=\"cl-page\"").contains("cl-page-body is-wide");
        assertThat(countOf(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("fragments/screen-intro :: toggle").contains("fragments/screen-intro :: panel('orders'");
        // the guide toggle follows the title, as on Asortyment and the settings pages, not the header actions
        assertThat(html.indexOf("fragments/screen-intro :: toggle")).isBetween(html.indexOf("cl-page-title"), html.indexOf("cl-page-actions"));
    }

    @Test
    void tilesSegmentsToolbarChipsTableAndPagingAreWiredToTheModel() throws Exception {
        String html = page();
        assertThat(html).contains("cl-stat-grid is-orders").contains("cl-stat-value").contains("cl-stat-hint")
                .doesNotContain("cl-stat-icon").doesNotContain("cl-stat-sum").doesNotContain("cl-stat-value-of")
                .contains("tile.kind().name() != 'Decide'").doesNotContain("cl-attention")
                .doesNotContain("cl-tabs").doesNotContain("cl-tab-count")
                .contains("class=\"cl-table-toolbar\"").contains("class=\"cl-toolbar-filters\"").doesNotContain("cl-list-controls")
                .doesNotContain("class=\"cl-menu").doesNotContain("fa-filter").contains("orders.list.filter.none")
                .contains("details class=\"cl-filter-menu is-status\" data-cl-filter-menu=\"status\"").contains("data-cl-autosubmit")
                .contains("name=\"status\"").contains("cl-filter-menu-check").contains("cl-filter-menu-group").contains("page.statusSummary()")
                .contains("data-cl-autosubmit-hide").contains("q.withStatus(null).href()")
                .contains("details class=\"cl-filter-menu\" data-cl-filter-menu=\"filter\"").contains("cl-filter-menu-item")
                .contains("q.withFilterId(o.id()).href()").contains("q.withFilterId('').href()")
                .contains("data-cl-dialog-open=\"save-view-dialog\"").contains("data-cl-dialog-open=\"filters-dialog\"")
                .contains("cl-search-form").contains("name=\"q\"").contains("cl-search-clear").contains("q.withQ(null).href()")
                .contains("cl-list-meta").contains("cl-filter-chips").contains("cl-table-results").contains("role=\"status\"")
                .contains("'cl-visually-hidden'").contains("cl-filter-chip-link").contains("chip.linkHref()")
                .contains("cl-table is-orders").contains("cl-table-sort").contains("aria-sort")
                .contains("cl-table-sortbar").contains("orders.list.sort.label")
                .contains("orders.list.sort.due").contains("orders.list.sort.amount").contains("orders.list.sort.number")
                .contains("orders.list.sort.ordered")
                .contains("fragments/pagination :: pages(${page.pagination()})")
                .contains("data-cl-orders-results").contains("data-cl-orders-nav")
                .contains("cl-list-empty").contains("orders.new.pos.button");
    }

    @Test
    void noBulmaWidgetsNoInlineStylesNoHardcodedText() throws Exception {
        String html = page();
        assertThat(html).doesNotContain("style=").doesNotContain("onclick=").doesNotContain("class=\"button")
                .doesNotContain("class=\"box\"").doesNotContain("notification is-").doesNotContain("dropdown")
                .doesNotContain("modal").doesNotContain("is-primary is-selected").doesNotContain("table is-striped")
                .doesNotContain("fa-cash-register").doesNotContain("<select");
        // every visible text goes through a message key: no Polish/English words as tag text
        Matcher text = Pattern.compile(">\\s*[A-Za-zĄ-ż][^<{#]{3,}<").matcher(html.replaceAll("<!--.*?-->", ""));
        assertThat(text.find()).as("literal text found: " + (text.hitEnd() ? "" : text.group())).isFalse();
    }

    @Test
    void scriptsAreIncluded() throws Exception {
        assertThat(page()).contains("@{/js/orders-list.js}").contains("@{/js/dialog.js}").contains("@{/js/filters-dialog.js}")
                .contains("@{/js/confirm-dialog.js}");
    }

    private static int countOf(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    private static String filters() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/orders/filters.html"), StandardCharsets.UTF_8);
    }

    @Test
    void filterDialogsCarryTheStarTheReturnAddressAndWorkAsPages() throws Exception {
        String html = filters();
        assertThat(html).contains("th:fragment=\"filtersDialog\"").contains("th:fragment=\"saveViewDialog\"")
                .contains("th:fragment=\"dialogBody\"").contains("id=\"filters-dialog\"").contains("id=\"save-view-dialog\"")
                .contains("cl-dialog is-form").contains("data-cl-dialog-body").contains("data-cl-dialog-close")
                .contains("class=\"cl-star\"").contains("aria-pressed").contains("/default").contains("default/clear")
                .contains("name=\"returnTo\"").contains("name=\"makeDefault\"").contains("data-cl-confirm")
                .contains("orders.filters.field.status.help").contains("th:if=\"${canManageStoreFilters}\"")
                .doesNotContain("style=").doesNotContain("onclick=").doesNotContain("class=\"button");
        assertThat(page()).contains("orders/filters :: filtersDialog").contains("orders/filters :: saveViewDialog");
    }

    @Test
    void rejectedFilterFormsKeepWhatTheUserSubmitted() throws Exception {
        String html = filters();
        assertThat(html).contains("th:value=\"${filterForm?.label}\"")
                .contains("th:value=\"${filterId}\"")
                .contains("name=\"dialog\" value=\"save-view\"")
                .contains("th:selected=\"${filterForm != null and #strings.equalsIgnoreCase(filterForm.status, status.name())}\"");
    }
}
