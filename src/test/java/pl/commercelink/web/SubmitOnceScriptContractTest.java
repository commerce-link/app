package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** static/js/submit-once.js, read as source: the repository has no JavaScript test runner. */
class SubmitOnceScriptContractTest {

    private static String script() throws Exception {
        return Files.readString(Path.of("src/main/resources/static/js/submit-once.js"), StandardCharsets.UTF_8);
    }

    @Test
    void busyLabelReplacesTheButtonTextWithASpinner() throws Exception {
        // when
        String js = script();

        // then
        assertThat(js).contains("getAttribute('data-cl-busy-label')").contains("'cl-spinner is-compact'")
                .contains("setAttribute('data-cl-idle-label'");
    }

    @Test
    void busyNoteIsRevealedAndEverythingIsRestoredFromTheBackForwardCache() throws Exception {
        // when
        String js = script();

        // then
        assertThat(js).contains("[data-cl-busy-note]").contains("note.hidden = false").contains("note.hidden = true")
                .contains("getAttribute('data-cl-idle-label')");
    }

    @Test
    void formsWithoutTheAttributesKeepTheOldBehaviour() throws Exception {
        // when
        String js = script();

        // then — the button is still disabled after the submit event, so its own value is sent
        assertThat(js).contains("button.disabled = true").contains("form.getAttribute('aria-busy') === 'true'");
    }
}
