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
     * Like the hooks of inventory.js above: the script of "Kategoria katalogu" on the review that still looks for a
     * renamed hook does nothing, and no server-side test would notice.
     */
    @Test
    void reviewTargetScriptUsesOnlyHooksTheReviewRenders() throws Exception {
        // given
        String script = read("src/main/resources/static/js/review-target.js");
        String template = read("src/main/resources/templates/catalog/products-add-review.html");

        // when / then
        for (String hook : List.of("data-review-target", "data-review-target-change", "data-review-target-actions")) {
            assertThat(script).as("script references " + hook).containsAnyOf(hook, datasetName(hook));
            assertThat(template).as("template renders " + hook).contains(hook);
        }
    }

    @Test
    void buildingScriptUsesOnlyHooksTheTemplatesRender() throws Exception {
        // given
        String script = read("src/main/resources/static/js/inventory-building.js");
        String templates = read("src/main/resources/templates/fragments/inventory-browse.html");

        // when / then
        for (String hook : List.of("data-browse-building", "data-browse-building-stalled", "data-cl-list-results",
                "data-cl-list-fragment", "data-cl-list-focus")) {
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
