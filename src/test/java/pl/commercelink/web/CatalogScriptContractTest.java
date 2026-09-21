package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogScriptContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static final List<String> TABLE_SCRIPTS = List.of(
            "src/main/resources/static/js/table-filter.js",
            "src/main/resources/static/js/table-sort.js",
            "src/main/resources/static/js/table-select.js");

    @Test
    void tableFilterSupportsFilterGroupsSelectsAndCounts() throws Exception {
        // given
        String script = read("src/main/resources/static/js/table-filter.js");

        // then
        for (String hook : List.of("data-cl-filter-group", "data-cl-filter-select", "data-cl-filter-multi", "data-count",
                "data-label", "data-cl-filter-clear", "cl:table-filtered", "replaceState", "data-cl-filter-value")) {
            assertThat(script).as("table-filter.js handles " + hook).contains(hook);
        }
    }

    @Test
    void tableSelectDrivesTheSelectionBarAndTheBulkForm() throws Exception {
        // given
        String script = read("src/main/resources/static/js/table-select.js");

        // then
        for (String hook : List.of("data-cl-select-table", "data-cl-select-all", "data-cl-select-row", "data-cl-selection-bar",
                "data-cl-selection-count", "data-cl-select-action", "data-cl-select-clear", "data-cl-select-form",
                "data-cl-select-confirm-title", "data-cl-select-confirm-message", "data-cl-select-confirm-action",
                "is-selected", "cl:table-filtered")) {
            assertThat(script).as("table-select.js handles " + hook).contains(hook);
        }
    }

    /**
     * The scripts are plain IIFEs loaded with `defer`; a browser dialog would sit outside the design system and, on a
     * bulk action, outside the confirmation the page already has.
     */
    @Test
    void everyTableScriptIsStrictAndAsksNothingThroughTheBrowser() throws Exception {
        // given / when / then
        for (String path : TABLE_SCRIPTS) {
            String script = read(path);
            assertThat(script).as(path + " runs in strict mode").contains("'use strict';");
            assertThat(script).as(path + " uses no browser dialog")
                    .doesNotContain("alert(").doesNotContain("confirm(").doesNotContain("prompt(");
        }
    }

    @Test
    void tableSortExistsAndUsesAriaSort() throws Exception {
        // given
        String script = read("src/main/resources/static/js/table-sort.js");

        // then
        assertThat(script).contains("aria-sort").contains("data-sort-key").contains("cl-table-sort");
    }

    @Test
    void repeatFieldsAnnounceAddedGroupsAndVariantFieldsListen() throws Exception {
        // given
        String repeat = read("src/main/resources/static/js/repeat-fields.js");
        String variant = read("src/main/resources/static/js/variant-fields.js");

        // then
        assertThat(repeat).contains("cl:repeat-added");
        assertThat(variant).contains("cl:repeat-added");
    }
}
