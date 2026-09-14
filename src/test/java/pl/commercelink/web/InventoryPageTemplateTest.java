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
        assertThat(page()).contains("th:action=\"@{/dashboard/inventory}\"").contains("method=\"get\"").contains("name=\"q\"");
    }

    @Test
    void exposesTheHooksTheScriptSwapsFragmentsInto() throws Exception {
        assertThat(page()).contains("data-inventory-page").contains("data-inventory-summary").contains("id=\"inventory-results\"")
                .contains("aria-live=\"polite\"").contains("data-inventory-search-error");
    }

    @Test
    void loadsThePageStylesAndScript() throws Exception {
        assertThat(page()).contains("@{/css/inventory.css}").contains("@{/js/inventory.js}");
    }
}
