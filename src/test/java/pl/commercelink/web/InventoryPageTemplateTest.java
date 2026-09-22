package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPageTemplateTest {

    private String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/inventory.html"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    void searchFormWorksWithoutJavaScriptAsAPlainGet() throws Exception {
        // when / then
        assertThat(page()).contains("th:action=\"@{/dashboard/inventory}\"").contains("method=\"get\"").contains("name=\"q\"")
                .doesNotContain("th:disabled");
    }

    @Test
    void exposesTheHooksTheScriptSwapsFragmentsInto() throws Exception {
        // when / then
        assertThat(page()).contains("data-inventory-page").contains("data-inventory-summary").contains("id=\"inventory-results\"")
                .contains("aria-live=\"polite\"").contains("data-inventory-search-error");
    }

    @Test
    void checkButtonKeepsOneLabelWhileTheResultsShowASpinner() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("data-when-busy").doesNotContain("data-when-idle")
                .contains("data-inventory-spinner").contains("class=\"cl-spinner\"");
    }

    @Test
    void loadsThePageStylesAndScript() throws Exception {
        // when / then
        assertThat(page()).contains("@{/css/inventory.css}").contains("@{/js/inventory.js}");
    }
}
