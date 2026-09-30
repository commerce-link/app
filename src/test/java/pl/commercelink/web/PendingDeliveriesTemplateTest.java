package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesTemplateTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + path), StandardCharsets.UTF_8);
    }

    @Test
    void pageSwapsItsResultsThroughTheSharedListScript() throws Exception {
        // when
        String page = read("deliveries/pending.html");

        // then
        assertThat(page).contains("data-cl-list-results").contains("th:fragment=\"results\"")
                .contains("data-cl-list-path=${page.listPath()}").contains("data-cl-list-fragment=${page.fragmentPath()}")
                .contains("/js/list-page.js").contains("/js/row-toggle.js")
                .contains("cl-segmented").contains("${page.tabs()}").contains("cl-stat is-link").doesNotContain("is-static");
    }

    @Test
    void menusAndSearchDoNotCarryTheTab() throws Exception {
        // when
        String fragments = read("fragments/deliveries-pending.html");

        // then — a narrowing form lets the page pick the tab with results (spec §3.6)
        assertThat(fragments).contains("th:fragment=\"hidden(skip)\"").doesNotContain("name=\"kind\"");
    }

    @Test
    void noBulmaWidgetsNoInlineStylesNoHardcodedText() throws Exception {
        for (String path : new String[]{"deliveries/pending.html", "fragments/deliveries-pending.html"}) {
            // when
            String html = read(path);

            // then
            assertThat(html).as(path).doesNotContain("style=").doesNotContain("onclick=").doesNotContain("class=\"button")
                    .doesNotContain("class=\"box\"").doesNotContain("class=\"level").doesNotContain("class=\"tag")
                    .doesNotContain("class=\"table").doesNotContain("class=\"notification").doesNotContain("th:utext");
            assertThat(html.replaceAll("<!--/\\*.*?\\*/-->", "")).as(path).doesNotContainPattern(">\\s*[A-Za-zĄ-ż][^<{#]{3,}<");
        }
    }
}
