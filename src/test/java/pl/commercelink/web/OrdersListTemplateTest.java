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
}
