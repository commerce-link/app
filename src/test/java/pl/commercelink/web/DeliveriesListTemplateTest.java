package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveriesListTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/deliveries.html"), StandardCharsets.UTF_8);
    }

    @Test
    void oneH1TheSharedShellAndTheListScript() throws Exception {
        // when
        String html = page();

        // then
        assertThat(html).contains("layout:decorate=\"~{layout}\"").contains("class=\"cl-page\"")
                .contains("cl-page-body is-wide").contains("fragments/screen-intro :: panel('deliveries', 'fas fa-truck')")
                .contains("th:fragment=\"results\"").contains("data-cl-list-results")
                .contains("data-cl-list-path=\"/dashboard/deliveries\"").contains("data-cl-list-fragment=\"/dashboard/deliveries/list\"")
                .contains("/js/list-page.js");
        assertThat(Pattern.compile("<h1").matcher(html).results().count()).isEqualTo(1);
    }

    @Test
    void tilesScopeMenusChipsTableAndPagingAreWiredToTheModel() throws Exception {
        // when / then
        assertThat(page()).contains("${page.tiles()}").contains("class=\"cl-stat is-link\"").contains("${page.scopes()}")
                .contains("class=\"cl-segment\"").contains("data-cl-filter-menu=\"state\"").contains("data-cl-filter-menu=\"provider\"")
                .contains("data-cl-filter-menu=\"settle\"").contains("data-cl-filter-menu=\"dates\"")
                .contains("data-cl-toolbar-toggle").contains("${page.chips()}").contains("class=\"cl-table-results\"")
                .contains("cl-table is-orders is-deliveries").contains("class=\"cl-row-link\"").contains("cl-table-sortbar")
                .contains("fragments/pagination :: pages(${page.pagination()})").contains("${page.emptyState()}")
                .contains("deliveries.dropship.badge").contains("is-secondary-column").contains("cl-table-sortbar is-wrap");
    }

    @Test
    void filtersToggleKeepsLabelAndCountInOneSpanSoTheFlexGapDoesNotSplitTheColon() throws Exception {
        // when
        String html = page();
        int toggle = html.indexOf("data-cl-toolbar-toggle");
        String button = html.substring(toggle, html.indexOf("</button>", toggle));

        // then
        assertThat(button).contains("cl-toolbar-toggle-label");
        assertThat(button.indexOf("cl-toolbar-toggle-label")).isLessThan(button.indexOf("deliveries.list.filters"))
                .isLessThan(button.indexOf("activeFilterCount"));
    }

    @Test
    void superAdminColumnAndCreateButtonAreConditional() throws Exception {
        // when / then
        assertThat(page()).contains("th:if=\"${page.superAdmin()}\"").contains("th:if=\"${page.canCreate()}\"")
                .contains("/dashboard/deliveries/preview");
    }

    @Test
    void noBulmaWidgetsNoInlineStylesNoHardcodedText() throws Exception {
        // given
        String html = page();

        // when
        Matcher text = Pattern.compile(">\\s*[A-Za-zĄ-ż][^<{#]{3,}<").matcher(html.replaceAll("(?s)<!--.*?-->", ""));

        // then
        assertThat(html).doesNotContain("style=").doesNotContain("onclick=").doesNotContain("onchange=")
                .doesNotContain("class=\"button").doesNotContain("class=\"box\"").doesNotContain("class=\"tag")
                .doesNotContain("class=\"table").doesNotContain("<select").doesNotContain("th:utext")
                .doesNotContain("<script>").doesNotContain("fragments/pagination :: pagination(");
        assertThat(text.find()).as("literal text found").isFalse();
    }
}
