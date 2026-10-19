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

    /** Every timeline is drawn by cl-timeline in commercelink.css; no page uses the bulma-timeline classes any more. */
    @Test
    void loadsNoTimelineStylesheetFromTheCdn() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).doesNotContain("bulma-timeline");
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
    void pinsTheTimelineStylesheetInsteadOfTrackingLatest() throws Exception {
        // when
        String html = layout();

        // then
        assertThat(html).doesNotContain("@latest");
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
