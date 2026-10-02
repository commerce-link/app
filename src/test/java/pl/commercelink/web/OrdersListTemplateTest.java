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

    /** The dialog and the scripts sit outside the results block list-page.js swaps, so they survive every swap. */
    @Test
    void theDialogAndTheScriptsSitOutsideTheSwappedResults() throws Exception {
        // given
        String html = page();

        // when
        int pageEnd = html.lastIndexOf("</section>");
        int dialog = html.indexOf("<dialog th:replace=\"~{fragments/confirm-dialog :: dialog}\"></dialog>");
        int listPage = html.indexOf("<script th:src=\"@{/js/list-page.js}\" defer></script>");

        // then
        assertThat(dialog).isGreaterThan(pageEnd).isLessThan(listPage);
        assertThat(html.substring(pageEnd)).contains("<script th:src=\"@{/js/table-select.js}\" defer></script>")
                .contains("<script th:src=\"@{/js/print.js}\" defer></script>")
                .contains("<script th:src=\"@{/js/confirm-dialog.js}\" defer></script>");
    }
}
