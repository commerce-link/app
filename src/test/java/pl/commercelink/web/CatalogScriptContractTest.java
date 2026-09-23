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

    /**
     * The redesigned screens are built from the `cl-*` layer: a Bulma widget class, a browser dialog or an inline
     * style on one of them is a regression back to the old catalog, not a local styling choice.
     */
    @Test
    void newTemplatesUseNoBulmaWidgetsOrInlineStyles() throws Exception {
        // given
        Path dir = Path.of("src/main/resources/templates/catalog");

        // when / then
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                assertThat(html).as(file + " has no inline style").doesNotContain(" style=\"");
                assertThat(html).as(file + " has no Bulma widgets")
                        .doesNotContain("class=\"dropdown")
                        .doesNotContain("class=\"notification")
                        .doesNotContain("class=\"box\"")
                        // Bulma's own button, not the design system's cl-button / cl-link-button, which carry the
                        // same is-primary / is-danger modifiers
                        .doesNotContain("class=\"button")
                        .doesNotContain("class=\"tag");
                assertThat(html).as(file + " has no browser dialogs")
                        .doesNotContain("alert(").doesNotContain("confirm(");
            }
        }
    }

    /** The tree picker is shared with the product page, so it follows the same rules as the catalog templates. */
    @Test
    void theSharedCategoryPickerCarriesNoBulmaWidgetsOrInlineStyles() throws Exception {
        // given
        String html = read("src/main/resources/templates/fragments/category-picker.html");

        // then
        assertThat(html).doesNotContain("<style").doesNotContain("class=\"dropdown")
                .doesNotContain("class=\"notification").doesNotContain("tag is-delete");
    }

    @Test
    void oldCatalogTemplatesAreGone() {
        // when / then
        assertThat(Files.exists(Path.of("src/main/resources/templates/catalogDetails.html"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/resources/templates/catalogs.html"))).isFalse();
        assertThat(Files.exists(Path.of("src/main/java/pl/commercelink/web/ProductCatalogController.java"))).isFalse();
    }

    /**
     * The product column asks for 300 px so the name and the codes stand apart; unguarded, that minimum was wider
     * than the page itself on a narrow window and in card mode, which is horizontal scrolling on every catalog table.
     * The guard uses a breakpoint of the design system (720 / 1024 / 1216), not one of its own.
     */
    @Test
    void productColumnAsksForItsMinimumWidthOnlyOnTheWidestBreakpoint() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css).contains("""
                @media screen and (min-width: 1216px) {
                    .cl-page .cl-table.is-products .cl-table-key {
                        min-width: 300px;
                    }
                }""");
        assertThat(css).doesNotContain("min-width: 1100px");
    }

    /**
     * The name of a product is a link sitting right above the codes of the same cell, so colour alone may not be what
     * tells it apart from them (WCAG 1.4.1), and a segment with no matches may not be tinted below the contrast its
     * size needs.
     */
    @Test
    void aLinkInATableCellIsUnderlinedAndNoSegmentIsTintedBelowTheContrastThreshold() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css).contains("""
                .cl-page .cl-table .cl-table-key a {
                    color: var(--cl-ink);
                    text-decoration: underline;""");
        assertThat(css).doesNotContain(".cl-segment[data-count=\"0\"] {");
    }

    /**
     * In card mode the column name stands in an 84 px column beside the value. The cell of a number does not wrap
     * (the number must not break), and that applied to the name as well, which then ran under the value.
     */
    @Test
    void columnNameOfACardCellWrapsInsteadOfRunningUnderTheValue() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // when
        String rule = css.split("\\.cl-page \\.cl-table :is\\(tbody th, td\\)::before \\{")[1].split("}")[0];

        // then
        assertThat(rule).contains("content: attr(data-label);").contains("white-space: normal;");
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

    /** min(34rem, 90vw) was wider than the field column on a phone: the open menu scrolled the page sideways (D-I3). */
    @Test
    void theOpenPickerMenuFitsThePhoneScreen() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css).contains("""
                @media screen and (max-width: 719px) {
                    .cl-page .cl-picker-menu {
                        min-width: 100%;
                        max-width: calc(100vw - 32px);
                    }
                }""");
    }

    /**
     * `--cl-ink-3` reaches 4.5:1 on white only: on the tinted backgrounds of the catalog it fell to 4.04:1 (the accent
     * soft of a chip, an active picker option, a selected row) and 4.47:1 (the segmented control), below AA for text of
     * that size. Those four contexts use `--cl-ink-2` (6.37:1), D-C2.
     */
    @Test
    void mutedTextOnATintedBackgroundUsesTheDarkerGrey() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css).contains("""
                .cl-page .cl-table tr.is-selected .cl-table-sub {
                    color: var(--cl-ink-2);""");
        assertThat(css).contains("""
                .cl-page .cl-chip-tag-path {
                    color: var(--cl-ink-2);""");
        assertThat(css).contains("""
                .cl-page .cl-picker-option:is(:hover, .is-active) .cl-picker-path {
                    color: var(--cl-ink-2);""");
        assertThat(css).contains("""
                .cl-segment-count {
                    margin-left: 4px;
                    color: var(--cl-ink-2);""");
    }

    /**
     * A link in the line under a page title ("Dziś pasuje: 8 produktów", "Edytuj") sat in the Bulma link colour without
     * an underline, 1.33:1 against the text around it (axe link-in-text-block, D-C3). It looks like a link in a row
     * description.
     */
    @Test
    void aLinkInThePageLeadIsUnderlinedLikeALinkInARowDescription() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css).contains("""
                .cl-page .cl-page-lead a {
                    color: var(--cl-accent-ink);
                    text-decoration: underline;
                    text-underline-offset: 2px;
                }""");
    }

    /** The chip's remove button had the browser's default focus ring; it gets the one of a link button (D-M23). */
    @Test
    void theChipRemoveButtonAndAListTitleLinkShowTheDesignSystemFocusRing() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(css).contains("""
                .cl-page .cl-chip-tag-remove:focus-visible,
                .cl-page .cl-list-title a:focus-visible {
                    outline: 2px solid var(--cl-accent);
                    outline-offset: 2px;""");
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
        assertThat(css).contains("""
                .cl-page .cl-selection-bar .cl-button.is-primary {
                    background: var(--cl-accent);
                    color: var(--cl-surface);""");
        String chipRemove = css.split("\\.cl-page \\.cl-chip-tag-remove \\{")[1].split("}")[0];
        assertThat(chipRemove).contains("border-radius: calc(var(--cl-radius) - 2px);");
    }

    /**
     * The product cell of the review ("Uzupełnij dane") holds the brand and the two identifier fields. The cell did not
     * wrap (the key column of every table), so the fields stood side by side and the manufacturer code ran under the
     * label select of the next column, out of reach of the mouse (D-I2). They stand one under the other, each under
     * its visible label (D-M32), in the table and in card mode alike.
     */
    @Test
    void theIdentifierFieldsOfTheReviewStandOneUnderTheOther() throws Exception {
        // given
        String css = read("src/main/resources/static/css/commercelink.css");

        // then
        assertThat(rule(css, ".cl-page .cl-table.is-editable tbody th")).contains("white-space: normal;");
        assertThat(rule(css, ".cl-page .cl-table.is-editable tbody th .cl-input")).contains("display: block;");
        assertThat(rule(css, ".cl-page .cl-table.is-editable tbody th .cl-label")).contains("display: block;");
        assertThat(rule(css, ".cl-page .cl-table.is-editable tbody th.cl-table-key")).contains("display: block;");
    }
}
