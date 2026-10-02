package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PendingDeliveriesScriptsContractTest {

    private static String js(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/static/js/" + name), StandardCharsets.UTF_8);
    }

    @Test
    void theListScriptAnnouncesEverySwap() throws Exception {
        assertThat(js("list-page.js")).contains("new CustomEvent('cl-list:swapped', { bubbles: true })");
    }

    @Test
    void theRowToggleWorksAfterASwapAndKeepsAriaInStep() throws Exception {
        // when
        String toggle = js("row-toggle.js");

        // then
        assertThat(toggle).contains("'cl-list:swapped'").contains("aria-expanded").contains("data-cl-row-toggle-fallback")
                .contains("addEventListener('click'").doesNotContain("innerHTML");
    }
}
