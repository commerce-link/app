package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutShellTemplateTest {

    private String layout() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/layout.html"), StandardCharsets.UTF_8);
    }

    @Test
    void buildsTheShellFromTheNavigationFragments() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).contains("~{fragments/navigation :: sidebar}");
        assertThat(html).contains("~{fragments/navigation :: topbar}");
        assertThat(html).contains("cl-shell");
    }

    @Test
    void dropsTheHorizontalNavbarAndItsDuplicatedLinkLists() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).doesNotContain("navbar-start");
        assertThat(html).doesNotContain("navbar-burger");
        assertThat(html).doesNotContain("sec:authorize=\"hasRole('ADMIN')\"");
        assertThat(html).doesNotContain("scrollbar-width: none");
    }

    @Test
    void keepsExactlyOneContentFragmentSoEveryPageStillDecoratesIt() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html.split("layout:fragment=\"content\"", -1)).hasSize(2);
    }

    @Test
    void loadsTheShellStylesheetAfterBulma() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html.indexOf("bulma.min.css")).isLessThan(html.indexOf("/css/commercelink.css"));
    }

    @Test
    void offersASkipLinkAheadOfTheNavigation() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).contains("class=\"cl-skip\"");
        assertThat(html.indexOf("cl-skip")).isLessThan(html.indexOf("fragments/navigation :: sidebar"));
    }

    @Test
    void pinsEveryExternalStylesheetAndScriptToAVersion() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).doesNotContain("@latest");
    }
}
