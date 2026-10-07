package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import pl.commercelink.web.orders.OrderListQuery;
import pl.commercelink.web.orders.OrdersPageModel;
import pl.commercelink.web.orders.Pagination;
import pl.commercelink.web.settings.SettingsTemplateRenderer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class OrdersListTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/orders/list.html"), StandardCharsets.UTF_8);
    }

    private static final String PICKUP_HREF = "/dashboard/shipping/pickups/new?back=%2Fdashboard%2Forders%3Fstatus%3DShipping";

    // the page header alone (the rest of the page needs the whole filter model), on one line so the renderer takes it
    // for markup, rendered with Polish messages
    private static String header(OrdersPageModel.PickupAction pickup) throws Exception {
        String html = page();
        String markup = html.substring(html.indexOf("<header class=\"cl-page-header\">"),
                html.indexOf("</header>") + "</header>".length()).replaceAll("\\s+", " ");
        OrdersPageModel model = new OrdersPageModel(OrderListQuery.parse(new org.springframework.util.LinkedMultiValueMap<>()),
                List.of(), List.of(), "", List.of(), Optional.empty(), List.of(), "", Map.of(), List.of(),
                Pagination.of(1, 0, 50, n -> "/x"), null, pickup);
        Map<String, Object> variables = new HashMap<>();
        variables.put("page", model);
        return SettingsTemplateRenderer.render(markup, variables).replaceAll("\\s+", " ");
    }

    /** "Zamów odbiór" in the header, before "Nowa sprzedaż POS", with the number of packages waiting for a courier. */
    @Test
    void theHeaderOffersThePickupPageWithTheNumberOfWaitingPackages() throws Exception {
        // when
        String html = header(new OrdersPageModel.PickupAction(PICKUP_HREF, 5));

        // then
        assertThat(html).contains("<a class=\"cl-button is-page-action\" href=\"" + PICKUP_HREF + "\" data-cl-list-back>"
                        + " <i class=\"fas fa-shipping-fast\" aria-hidden=\"true\"></i> <span>Zamów odbiór</span>"
                        + " <span class=\"cl-button-count\"><span class=\"cl-visually-hidden\">paczki czekające na kuriera:</span>"
                        + "<span>5</span></span> </a>");
        assertThat(html.indexOf("Zamów odbiór")).isLessThan(html.indexOf("Nowa sprzedaż POS"));
    }

    /** Nothing waits: the action stays (the pickup page says so), without a number. */
    @Test
    void withNothingWaitingTheHeaderActionHasNoNumber() throws Exception {
        // when
        String html = header(new OrdersPageModel.PickupAction(PICKUP_HREF, null));

        // then
        assertThat(html).contains("href=\"" + PICKUP_HREF + "\"").contains("<span>Zamów odbiór</span>")
                .doesNotContain("cl-button-count");
    }

    /** The action is part of the header only: outside the results block that list-page.js swaps. */
    @Test
    void thePickupActionSitsInTheHeaderOutsideTheResultsBlock() throws Exception {
        // given
        String html = page();

        // then
        assertThat(html.indexOf("page.pickup().href()")).isPositive()
                .isLessThan(html.indexOf("</header>"))
                .isLessThan(html.indexOf("data-cl-list-results"));
    }

    /** One way to clear every narrowing at once, right after the filter menu in the toolbar, only when something narrows. */
    @Test
    void clearFiltersSitsNextToTheFilterMenu() throws Exception {
        // given
        String html = page();

        // when
        int filterMenu = html.indexOf("data-cl-filter-menu=\"filter\"");
        int clear = html.indexOf("q.cleared().href()");

        // then
        assertThat(clear).isGreaterThan(filterMenu).isLessThan(html.indexOf("class=\"cl-search-form\""));
        assertThat(html).contains("class=\"cl-link-button cl-toolbar-clear\"").contains("#{general.clear.filters}")
                .doesNotContain("cl-filter-chips-clear");
        assertThat(html.substring(html.lastIndexOf("<a", clear), clear)).contains("th:if=\"${!page.chips().isEmpty()}\"");
    }

    /** The four tiles narrow the list: the same cl-stat, as a link, pressed state for the one narrowing now. */
    @Test
    void tilesAreLinksThatNarrowTheList() throws Exception {
        // given
        String html = page();

        // when
        int grid = html.indexOf("cl-stat-grid is-orders");
        String tiles = html.substring(grid, html.indexOf("</ul>", grid));

        // then
        assertThat(tiles).contains("<a class=\"cl-stat is-link\"").contains("th:href=\"@{${tile.href()}}\"")
                .contains("aria-current=${tile.active()} ? 'true' : null").contains("data-cl-list-nav")
                .contains("#{orders.list.attention.active}").contains("cl-visually-hidden");
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

    private static String filters() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/orders/filters.html"), StandardCharsets.UTF_8);
    }

    @Test
    void rejectedFilterFormsKeepWhatTheUserSubmitted() throws Exception {
        String html = filters();
        assertThat(html).contains("th:value=\"${filterForm?.label}\"")
                .contains("${statuses.![name()]}, ${filterForm?.status})");
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    /** Printing several cards: a checkbox column before the order number, with the order's id as its value. */
    @Test
    void everyRowHasACheckboxOfItsOrderBeforeTheRowLink() throws Exception {
        // given
        String html = page();

        // when
        int table = html.indexOf("<table class=\"cl-table is-orders\"");
        String tag = html.substring(table, html.indexOf('>', table));
        int headCheck = html.indexOf("<th scope=\"col\" class=\"cl-table-check\">", table);
        int firstSortHead = html.indexOf("aria-sort=${page.sortHeaders().get(numberSort).ariaSort()}", table);
        int rowCheck = html.indexOf("<td class=\"cl-table-check\">", table);
        String row = html.substring(rowCheck, html.indexOf("</td>", rowCheck));

        // then
        assertThat(tag).contains("data-cl-select-table=\"true\"");
        assertThat(headCheck).isPositive().isLessThan(firstSortHead);
        assertThat(rowCheck).isPositive().isLessThan(html.indexOf("class=\"cl-row-link\""));
        assertThat(html.substring(headCheck, html.indexOf("</th>", headCheck)))
                .contains("data-cl-select-all hidden").contains("#{orders.list.select.all}").contains("#{orders.list.select.none}");
        assertThat(row).contains("<label class=\"cl-check-target\">").contains("data-cl-select-row hidden")
                .contains("autocomplete=\"off\"").contains("th:value=\"${row.orderId()}\"")
                .contains("#{orders.list.select.row(${row.number()})}");
    }

    /** The selection row (design system "Pasek zaznaczenia") is the only place that prints several cards. */
    @Test
    void theSelectionRowPrintsTheCardsAndIsTheOnlyPrintAction() throws Exception {
        // given
        String html = page();

        // when
        int bar = html.indexOf("data-cl-selection-bar");
        int table = html.indexOf("<table class=\"cl-table is-orders\"");
        String row = html.substring(html.lastIndexOf("<div", bar), table);
        String toolbar = html.substring(html.indexOf("<div class=\"cl-table-toolbar\">"), html.indexOf("<div class=\"cl-list-meta\""));

        // then
        assertThat(bar).isGreaterThan(html.indexOf("<nav class=\"cl-table-sortbar\"")).isLessThan(table);
        assertThat(row).contains("<div class=\"cl-selection-row is-wide-only\" hidden data-cl-selection-bar>")
                .contains("data-cl-selection-count").contains("#{orders.list.selected('{k}', '{n}')}")
                .contains("<button type=\"button\" class=\"cl-button is-primary\" data-cl-select-confirm-above=\"10\"")
                .contains("data-cl-select-print=@{/dashboard/orders/cards}")
                .contains("#{orders.list.cards.confirm.title.few('{n}')}").contains("#{orders.list.cards.confirm.title.many('{n}')}")
                .contains("#{orders.list.cards.confirm.message}")
                .contains("#{orders.list.cards.confirm.action.few('{n}')}").contains("#{orders.list.cards.confirm.action.many('{n}')}")
                .contains("fas fa-print").contains("data-cl-selection-text").contains("#{orders.list.cards.print('{k}')}")
                .contains("data-cl-select-clear").contains("#{orders.list.selection.clear}");
        assertThat(html.substring(html.lastIndexOf("<p", bar), bar)).contains("role=\"status\" data-cl-selection-status");
        assertThat(toolbar).doesNotContain("data-cl-select");
        assertThat(occurrences(html, "/dashboard/orders/cards")).isEqualTo(1);
    }

    /**
     * The dialog sits inside section.cl-page (its title and message are styled by `.cl-page .cl-dialog ...`) but after
     * the results block list-page.js swaps, so it survives every swap; the scripts follow the page.
     */
    @Test
    void theDialogSitsInsideThePageButOutsideTheSwappedResults() throws Exception {
        // given
        String html = page();

        // when
        int results = html.indexOf("data-cl-list-results");
        int pageEnd = html.lastIndexOf("</section>");
        int dialog = html.indexOf("<dialog th:replace=\"~{fragments/confirm-dialog :: dialog}\"></dialog>");
        int resultsEnd = html.lastIndexOf("</div>", html.lastIndexOf("</div>", pageEnd) - 1);
        int listPage = html.indexOf("<script th:src=\"@{/js/list-page.js}\" defer></script>");

        // then
        assertThat(dialog).isGreaterThan(resultsEnd).isLessThan(pageEnd);
        assertThat(results).isLessThan(resultsEnd);
        assertThat(pageEnd).isLessThan(listPage);
        assertThat(html.substring(pageEnd)).contains("<script th:src=\"@{/js/table-select.js}\" defer></script>")
                .contains("<script th:src=\"@{/js/print.js}\" defer></script>")
                .contains("<script th:src=\"@{/js/confirm-dialog.js}\" defer></script>");
    }
}
