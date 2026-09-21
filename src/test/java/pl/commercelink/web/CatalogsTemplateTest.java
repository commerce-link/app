package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogsTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/catalogs.html"), StandardCharsets.UTF_8);
    }

    @Test
    void listsCatalogsAsACardListWithOneHeading() throws Exception {
        // when / then
        assertThat(page()).contains("<h1 class=\"cl-page-title\"").contains("class=\"cl-list\"").contains("cl-list-item")
                .contains("th:each=\"catalog : ${catalogs}\"").contains("${catalog.href()}").contains("${catalog.settingsHref()}")
                .contains("#{catalog.list.desc(").contains("cl-list-empty").contains("#{catalog.add}");
    }

    @Test
    void usesNoBulmaButtonsInlineStylesOrPagination() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("is-primary\" th:href").doesNotContain("button is-").doesNotContain("style=")
                .doesNotContain("fragments/pagination").doesNotContain("<table");
    }
}
