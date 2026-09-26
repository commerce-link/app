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
                .doesNotContain("cl-stat-icon").doesNotContain("tile.tone()")  // values in ink, as on Asortyment
                .doesNotContain("cl-stat-sum").doesNotContain("cl-stat-value-of")
                .doesNotContain("tile.kind()").doesNotContain("cl-attention")
                .doesNotContain("cl-tabs").doesNotContain("cl-tab-count")
                .contains("class=\"cl-table-toolbar\"").contains("class=\"cl-toolbar-filters\"").doesNotContain("cl-list-controls")
                .doesNotContain("class=\"cl-menu").doesNotContain("fa-filter").contains("orders.list.filter.none")
                .contains("details class=\"cl-filter-menu is-status\" data-cl-filter-menu=\"status\"").contains("data-cl-autosubmit")
                .contains("name=\"status\"").contains("cl-filter-menu-check").contains("cl-filter-menu-group").contains("page.statusSummary()")
                .contains("data-cl-autosubmit-hide").contains("q.withStatus(null).href()")
                .contains("details class=\"cl-filter-menu\" data-cl-filter-menu=\"filter\"").contains("cl-filter-menu-item")
                .contains("th:href=\"@{${o.href()}}\"").contains("q.withFilterId(null).href()")   // a filter's link ticks its status
                .contains("@{/dashboard/orders/filters(returnTo=${returnTo})}")
                .doesNotContain("save-view").doesNotContain("saveView").doesNotContain("data-cl-dialog-open").doesNotContain("name=\"focus\"")
                .contains("cl-search-form").contains("name=\"q\"").contains("cl-search-clear").contains("cl-button is-primary cl-search-submit").contains("q.withQ(null).href()")
                .contains("cl-list-meta").contains("cl-filter-chips").contains("cl-table-results").contains("role=\"status\"")
                .contains("'cl-visually-hidden'").doesNotContain("cl-filter-chip-link").doesNotContain("historyStatuses")
                .contains("cl-table is-orders").contains("cl-table-sort").contains("aria-sort")
                .contains("cl-table-sortbar").contains("orders.list.sort.label")
                .contains("orders.list.sort.due").contains("orders.list.sort.amount").contains("orders.list.sort.number").contains("orders.list.sort.status")
                .contains("th:href=\"@{${page.sortHeaders().get(statusSort).href()}}\" th:text=\"#{orders.list.column.status}\"")
                .doesNotContain("orders.list.sort.ordered")   // "date placed" only sorted the history, which is gone
                .contains("fragments/pagination :: pages(${page.pagination()})")
                .contains("data-cl-orders-results").contains("data-cl-orders-nav")
                .contains("cl-list-empty").contains("orders.new.pos.button");
    }

    /** WZ · FV/PAR · review under the status pill (spec §25): a labelled list, state as a class, text for screen readers. */
    @Test
    void documentMarksSitUnderTheStatusPill() throws Exception {
        String html = page();
        int status = html.indexOf("th:text=\"${row.statusLabel()}\"");
        int marks = html.indexOf("<ul class=\"cl-doc-marks\"");
        assertThat(marks).isGreaterThan(status).isLessThan(html.indexOf("th:text=\"${row.totalText()}\""));
        assertThat(html).contains("aria-label=#{orders.list.marks.label}").contains("th:unless=\"${row.marks().isEmpty()}\"")
                .contains("${row.hasTodo()} ? 'has-todo'").contains("th:classappend=\"${mark.state()}\"")
                .contains("<span class=\"cl-visually-hidden\" th:text=\"${mark.label()}\"></span>")
                .contains("fas fa-check cl-doc-mark-icon").contains("fas fa-times cl-doc-mark-icon").contains("fas fa-star cl-doc-mark-icon").doesNotContain("fa-clock");
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
    void onlyTheListScriptIsIncluded() throws Exception {
        // no dialog is left on the list: "save this view" is gone and filters are managed on their own page
        assertThat(page()).contains("@{/js/orders-list.js}").doesNotContain("dialog.js").doesNotContain("confirm-dialog");
        assertThat(Path.of("src/main/resources/static/js/dialog.js")).doesNotExist();
        assertThat(Path.of("src/main/resources/templates/orders/filter-new.html")).doesNotExist();
    }

    private static int countOf(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    private static String filters() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/orders/filters.html"), StandardCharsets.UTF_8);
    }

    @Test
    void managementIsAPageWithAnEditSubpage() throws Exception {
        String html = filters();
        // the management page: list in a card, "Nowy filtr" in the card head, edit on a subpage, star and delete here
        assertThat(html).contains("layout:fragment=\"content\"").contains("cl-card-head").contains("/dashboard/orders/filters/add")
                .contains("/dashboard/orders/filters/{id}/edit").contains("data-cl-confirm")
                .doesNotContain("cl-star").doesNotContain("/default").doesNotContain("makeDefault").doesNotContain("is-filter")
                .contains("settings-header :: subpage(${listHref}")
                .doesNotContain("filtersDialog").doesNotContain("dialogBody").doesNotContain("data-cl-filter-edit")
                .doesNotContain("style=").doesNotContain("onclick=").doesNotContain("class=\"button");
        assertThat(html).doesNotContain("saveView").doesNotContain("th:fragment=\"redirect\"").doesNotContain("data-cl-dialog-body")
                .contains("aria-describedby=\"filter-conditions-help\"").contains("th:if=\"${canManageStoreFilters}\"");
        String edit = Files.readString(Path.of("src/main/resources/templates/orders/filter-edit.html"), StandardCharsets.UTF_8);
        assertThat(edit).contains("orders/filters :: filterFormFields").doesNotContain("name=\"dialog\"").contains("@{/js/order-filter-form.js}").contains("name=\"returnTo\"")
                .contains("cl-card-footer").contains("th:action=\"@{${formAction}}\"").contains("id=\"filter-conditions-help\"").contains("settings-header :: subpage(${returnTo}");
    }

    @Test
    void rejectedFilterFormsKeepWhatTheUserSubmitted() throws Exception {
        String html = filters();
        assertThat(html).contains("th:value=\"${filterForm?.label}\"")
                .contains("th:selected=\"${filterForm != null and #strings.equalsIgnoreCase(filterForm.status, status.name())}\"");
    }
}
