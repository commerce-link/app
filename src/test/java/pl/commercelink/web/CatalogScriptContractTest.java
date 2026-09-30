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

    /** The declarations of every rule of the style sheet whose selector list is exactly {@code selector}, joined. */
    private static String rule(String css, String selector) {
        StringBuilder body = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(selector) + " \\{([^}]*)}").matcher(css);
        while (matcher.find()) {
            body.append(matcher.group(1));
        }
        return body.toString();
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
                "data-cl-selection-count", "data-cl-select-action", "data-cl-select-form", "data-cl-label-select",
                "data-cl-label-clear",
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

    /**
     * The help-text margin reset was written for the catalog's own disclosure bodies, but a plain descendant
     * selector also matches `.cl-steps-note` (E-mail template) and `.cl-param-hint` (Reporting, courier account),
     * stripping their margins, and (fix round 1) `.cl-repeat-add p.cl-help` inside a disclosure body, which beats
     * the more specific `.cl-repeat-add .cl-help` rule (`:2848`) by specificity and forces the wrong line-height —
     * on the E-mail template's attachments disclosure (`store-email-template.html:101`, outside the catalog) and,
     * the same bug, on the catalog's own product page custom-filters disclosure (`product.html:235`). Every catalog
     * use of `.cl-disclosure-body p.cl-help` that IS meant to carry this rule (`product.html:163`,
     * `category-pricing.html:165`) sits inside a `.cl-form-grid`, so scoping the selector to `.cl-form-grid` keeps
     * that look while excluding the steps-note/param-hint paragraphs (never inside a form-grid) and every
     * `.cl-repeat-add` help text (inside `.cl-repeat`, not `.cl-form-grid`) catalog-wide, D-M1.
     */
    @Test
    void disclosureBodyHelpTextRuleOnlyReachesFormGridDescendantsInTheCatalog() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css)
                .as("the disclosure-body help rule is scoped to form-grid descendants, not any p.cl-help in the disclosure body")
                .contains(".cl-page .cl-disclosure-body .cl-form-grid p.cl-help:not(.cl-steps-note):not(.cl-param-hint) {")
                .doesNotContain(".cl-page .cl-disclosure-body p.cl-help {")
                .doesNotContain(".cl-page .cl-disclosure-body p.cl-help:not(.cl-steps-note):not(.cl-param-hint) {");
    }

    /**
     * The filters-card padding on `.cl-card > .cl-repeat` was meant for the catalog's category filters form, but the
     * bare selector also reaches the shipping/parcel template's repeat list, indenting its parcels by 20 px and
     * shifting "Usuń paczkę". Scoped to `#category-filters-form` so only the catalog filters page is affected (D-M1).
     */
    @Test
    void cardRepeatPaddingRuleIsScopedToTheCatalogFiltersForm() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css)
                .as("the filters-card repeat padding rule is scoped to the catalog filters form")
                .contains("#category-filters-form .cl-card > .cl-repeat {")
                .contains("#category-filters-form .cl-card > .cl-repeat > .cl-fieldset {")
                .doesNotContain(".cl-page .cl-card > .cl-repeat {")
                .doesNotContain(".cl-page .cl-card > .cl-repeat > .cl-fieldset {");
    }

    /**
     * Every class of the catalog templates and the shared tree picker belongs to the design system (`cl-*` with its
     * `is-*` modifiers) or to the icon font; `picker-option-*` and raw sizes/colours in their rules were not (D-M44).
     */
    @Test
    void catalogTemplatesUseOnlyDesignSystemClassesAndTheirRulesUseTokens() throws Exception {
        // given
        List<Path> templates;
        try (var files = Files.list(Path.of("src/main/resources/templates/catalog"))) {
            templates = new java.util.ArrayList<>(files.toList());
        }
        templates.add(Path.of("src/main/resources/templates/fragments/category-picker.html"));
        java.util.regex.Pattern classes = java.util.regex.Pattern.compile("class=\"([^\"$]*)\"|className = '([^']*)'");
        String css = read("src/main/resources/static/css/commercelink.css");

        // when / then
        for (Path file : templates) {
            java.util.regex.Matcher matcher = classes.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                for (String token : value.trim().split("\\s+")) {
                    assertThat(token).as(file + " class " + token)
                            .matches("cl-[a-z0-9-]+|is-[a-z0-9-]+|fas|fa-[a-z0-9-]+|icon");
                }
            }
        }
        assertThat(css).doesNotContain("picker-option-checkbox").doesNotContain(".picker-option-text")
                .doesNotContain("font-size: 11.5px");
        // the selection row takes the look of the table header it stands in for, not an accent box of its own
        assertThat(rule(css, ".cl-page .cl-selection-row")).contains("background: var(--cl-surface-2);")
                .contains("border-bottom: 1px solid var(--cl-line);").doesNotContain("--cl-accent");
        String chipRemove = css.split("\\.cl-page \\.cl-chip-tag-remove \\{")[1].split("}")[0];
        assertThat(chipRemove).contains("border-radius: calc(var(--cl-radius) - 2px);");
    }
}
