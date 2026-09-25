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
    }

    @Test
    void tilesSegmentsToolbarChipsTableAndPagingAreWiredToTheModel() throws Exception {
        String html = page();
        assertThat(html).contains("cl-stat-grid").contains("cl-stat is-link").contains("th:attr=\"aria-pressed=${tile.pressed()}")
                .contains("cl-segmented-divider").contains("aria-current").contains("cl-segment-count")
                .contains("cl-table-toolbar is-stacked").contains("cl-search-form").contains("name=\"q\"")
                .contains("cl-filter-chips").contains("cl-table-results").contains("role=\"status\"")
                .contains("cl-table is-orders").contains("cl-table-sort").contains("aria-sort")
                .contains("fragments/pagination :: pages(${page.pagination()})")
                .contains("data-cl-orders-results").contains("data-cl-orders-nav")
                .contains("cl-list-empty").contains("orders.new.pos.button");
    }

    @Test
    void noBulmaWidgetsNoInlineStylesNoHardcodedText() throws Exception {
        String html = page();
        assertThat(html).doesNotContain("style=").doesNotContain("onclick=").doesNotContain("class=\"button")
                .doesNotContain("class=\"box\"").doesNotContain("notification is-").doesNotContain("dropdown")
                .doesNotContain("modal").doesNotContain("is-primary is-selected").doesNotContain("table is-striped");
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
}
