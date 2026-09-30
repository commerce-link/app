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

    /**
     * Without it a screen reader reads a Polish page with an English voice, and `document.documentElement.lang`
     * (the collation of the sortable tables) falls back to a guess.
     */
    @Test
    void namesTheLanguageOfThePage() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).contains("<html th:lang=\"${#locale.language}\"");
    }

    /**
     * The item history page still draws its timeline with the `bulma-timeline` classes; dropping the stylesheet
     * turns it into an unstyled list. Remove the link only together with a restyle of that page.
     */
    @Test
    void keepsTheTimelineStylesheetWhileItemHistoryUsesIt() throws Exception {
        // given
        String itemHistory = Files.readString(Path.of("src/main/resources/templates/item-history.html"), StandardCharsets.UTF_8);

        // when
        String html = layout();

        // then
        if (itemHistory.contains("timeline")) {
            assertThat(html).contains("https://cdn.jsdelivr.net/npm/bulma-timeline@3.0.5/dist/css/bulma-timeline.min.css");
        }
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
    void pinsTheTimelineStylesheetInsteadOfTrackingLatest() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).doesNotContain("@latest");
    }

    @Test
    void loadsTheNavigationBehaviourScript() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).contains("/js/navigation.js");
    }

    /** async-form.js blames the connection only when the request failed; an answered 403 or 500 gets this message. */
    @Test
    void givesSaveWithoutReloadingAMessageForServerErrors() throws Exception {
        // when
        String layout = layout();
        String script = Files.readString(Path.of("src/main/resources/static/js/async-form.js"), StandardCharsets.UTF_8);

        // then
        assertThat(layout).contains("<body th:attr=\"data-cl-server-error=#{form.save.serverError}, data-cl-amount-format=#{general.currency.amount}\">");
        assertThat(script).contains("document.body.getAttribute('data-cl-server-error')");
    }
}
