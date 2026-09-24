package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.web.catalog.CatalogRow;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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
        assertThat(page()).doesNotContain("class=\"button is-").doesNotContain("style=")
                .doesNotContain("fragments/pagination").doesNotContain("<table");
    }

    private static CatalogRow named(String id, String name) {
        return new CatalogRow(id, name, 0, 0, 0, "daily", false, "/dashboard/catalogs/" + id,
                "/dashboard/catalogs/" + id + "/settings");
    }

    /**
     * RF-24: a catalog saved without a name by the old form (null, or an empty string) is listed as "(bez nazwy)" /
     * "(untitled)", so its link has text to click and its settings link a name to be read out with.
     */
    @Test
    void aCatalogWithoutANameIsListedAsUntitled() {
        // given
        Context context = new Context();
        context.setVariable("catalogs", List.of(named("c1", null), named("c2", ""), named("c3", "Parts")));

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/catalogs", context);

        // then
        assertThat(html).contains("<a href=\"/dashboard/catalogs/c1\">(untitled)</a>")
                .contains("<a href=\"/dashboard/catalogs/c2\">(untitled)</a>")
                .contains("<a href=\"/dashboard/catalogs/c3\">Parts</a>")
                .contains("aria-label=\"Settings: (untitled)\"")
                .doesNotContain("null").doesNotContain("??");
        assertThat(ResourceBundle.getBundle("messages", Locale.forLanguageTag("pl")).getString("settings.list.untitled"))
                .isEqualTo("(bez nazwy)");
    }
}
