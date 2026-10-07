package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryScriptContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void scriptUsesOnlyHooksTheTemplatesRender() throws Exception {
        // given
        String script = read("src/main/resources/static/js/inventory.js");
        String templates = read("src/main/resources/templates/inventory.html")
                + read("src/main/resources/templates/inventory-prices.html")
                + read("src/main/resources/templates/fragments/inventory-summary.html")
                + read("src/main/resources/templates/fragments/inventory-results.html")
                + read("src/main/resources/templates/fragments/inventory-technical.html");

        // when / then
        for (String hook : List.of("data-inventory-page", "data-inventory-summary", "data-inventory-search",
                "data-inventory-clear", "data-inventory-submit", "data-inventory-field-error", "data-inventory-search-error",
                "data-inventory-search-retry", "data-inventory-live", "data-inventory-empty-template",
                "data-inventory-slot", "data-inventory-sources-toggle", "data-when-collapsed", "data-when-expanded",
                "data-inventory-spinner", "data-inventory-warehouse-products", "data-warehouse-products-label", "data-inventory-results-heading", "data-inventory-offers",
                "data-inventory-sortable", "data-sort-key", "data-announce", "data-inventory-fragment")) {
            assertThat(script).as("script references " + hook).contains(hook);
            assertThat(templates).as("templates render " + hook).contains(hook);
        }
    }

    /**
     * Like the hooks of inventory.js above: a script of the browse list that still looks for a renamed hook does nothing,
     * and no server-side test would notice.
     */
    @Test
    void browseScriptUsesOnlyHooksTheTemplatesRender() throws Exception {
        // given
        String script = read("src/main/resources/static/js/inventory-browse.js");
        String templates = read("src/main/resources/templates/inventory.html")
                + read("src/main/resources/templates/fragments/inventory-browse.html");

        // when / then
        for (String hook : List.of("data-browse-dialog-url", "data-browse-dialog-slot", "data-browse-add",
                "data-browse-add-selected", "data-browse-add-error", "data-browse-other-select", "data-browse-other-radio",
                "data-browse-other-field", "data-cl-select-row", "data-browse-building")) {
            assertThat(script).as("script references " + hook).containsAnyOf(hook, datasetName(hook));
            assertThat(templates).as("templates render " + hook).contains(hook);
        }
    }

    private static String datasetName(String hook) {
        String[] parts = hook.substring("data-".length()).split("-");
        StringBuilder name = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return name.toString();
    }
}
