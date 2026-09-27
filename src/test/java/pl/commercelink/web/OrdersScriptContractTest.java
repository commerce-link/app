package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The orders screens rely on shared components; this keeps them in the shared stylesheet and scripts. */
class OrdersScriptContractTest {

    static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    static String css() throws Exception {
        return read("src/main/resources/static/css/commercelink.css");
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

    @Test
    void theSharedStylesheetCarriesEveryNewComponentOnce() throws Exception {
        // given
        String css = css();

        // then: only top-level rules count (selector at column 0) — the accepted makiety CSS legitimately repeats
        // some of these selectors, indented, inside @media blocks to override a property at a breakpoint
        for (String selector : List.of(".cl-page .cl-menu {", ".cl-page .cl-menu-list {", ".cl-page .cl-layout-aside {",
                ".cl-page .cl-timeline {", ".cl-page .cl-copy-inline {", ".cl-page .cl-record-title {",
                ".cl-page dl.cl-kv {", ".cl-status.is-bad {", ".cl-page .cl-dialog.is-form {",
                ".cl-page .cl-address {", ".cl-page .cl-list-summary {",
                ".cl-page .cl-card-toggle {", ".cl-page .cl-card-grid {", ".cl-page.cl-dialog-host {")) {
            String topLevel = "\n" + selector;
            assertThat(css).as(selector).contains(topLevel);
            assertThat(css.split(java.util.regex.Pattern.quote(topLevel), -1)).as("one top-level definition of " + selector)
                    .hasSize(2);
        }
    }

    /**
     * The record header (order details) is the only settings-header variant whose actions stretch full-width on
     * phones; every other settings-header page (catalog, store settings, notifications, ...) shares the same
     * {@code .cl-page-header-row > .cl-page-actions} markup and must keep main's phone layout, so the rule is scoped
     * to the {@code is-record} modifier the record fragment renders on its header row.
     */
    @Test
    void thePhoneActionsWidthRuleIsScopedToTheRecordHeader() throws Exception {
        // given
        String css = css();

        // then
        assertThat(css).doesNotContain(".cl-page-header-row .cl-page-actions {")
                .contains(".cl-page-header-row.is-record .cl-page-actions {");
    }

    @Test
    void menuDialogAndCollapseScriptsExistWithTheirContracts() throws Exception {
        // given
        String menu = read("src/main/resources/static/js/menu.js");
        String dialog = read("src/main/resources/static/js/dialog.js");
        String collapse = read("src/main/resources/static/js/collapse.js");
        String copy = read("src/main/resources/static/js/copy-field.js");

        // then
        // the menus are native details (spec §4.10): the script enhances them and never hides the list itself
        assertThat(menu).contains("details.cl-menu").contains("'toggle'").contains(":scope > summary")
                .contains("Escape").contains("ArrowDown").contains("aria-disabled").contains("is-up").contains(".open = false")
                .doesNotContain(".hidden =").doesNotContain("aria-controls");
        assertThat(dialog).contains("data-cl-dialog-open").contains("data-cl-dialog-close").contains("showModal")
                .contains("cl:dialog-open").contains("details.cl-menu").contains(":scope > summary");
        assertThat(collapse).contains("data-cl-collapse").contains("cl-card-toggle").contains("(max-width: 719px)");
        assertThat(copy).contains("cl-copy-inline").contains("data-cl-copy").contains("CL_TOAST_COPIED");
        for (String script : List.of(menu, dialog, collapse, copy)) {
            assertThat(script).contains("'use strict'").doesNotContain("alert(").doesNotContain("confirm(");
        }
    }

    /**
     * Task 10 CARRY (from Task 7): a card marked data-cl-collapse re-enhances on cl:form-replaced (the order settings
     * form saved without reloading), which used to always collapse it back on phones — hiding a 422 validation error
     * that async-form.js focuses right after the same event. The re-collapse must stay open when the swapped-in body
     * carries data-cl-error-summary.
     */
    @Test
    void aCollapsedCardReopensOnAValidationErrorAfterAnAsyncSave() throws Exception {
        // given
        String collapse = read("src/main/resources/static/js/collapse.js");

        // then
        assertThat(collapse).contains("data-cl-error-summary").containsPattern("var open = [^;]*data-cl-error-summary")
                .contains("body.hidden = !open");
    }

    @Test
    void theItemsScriptCountsTheScopeAndConfirmsInThePageDialog() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-items.js");

        // then
        assertThat(script).contains("data-cl-bulk-scope").contains("data-cl-bulk-action").contains("data-cl-scope-template")
                .contains("cl-confirm-dialog").contains("order-items-form").contains("'use strict'")
                .doesNotContain("confirm(").doesNotContain("alert(");
    }

    @Test
    void theItemDialogsScriptKeepsTheOldBehaviourWithoutInlineHandlers() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-item-dialogs.js");

        // then
        assertThat(script).contains("cl:dialog-open").contains("CL_SPLIT_PREVIEWS").contains("rows[")
                .contains("data-cl-cost-type").contains("data-url").contains("distribute").contains("renumber")
                .contains("'use strict'").doesNotContain("innerHTML =").doesNotContain("onclick")
                // Enter in the SKU search field must only filter, not implicitly submit the assign-sku form.
                .contains("search.addEventListener('keydown'");
        assertThat(read("src/main/resources/static/js/supplier-choice.js")).contains("data-cl-custom-option").contains("data-cl-custom-field");
        assertThat(css()).contains(".cl-page .cl-input-row {");
    }

    /**
     * Task 13 fix round 1 (Important 1): D-23 mapped item-add-check to cl-table-check, but the class landed on a
     * decorative span instead of the cell the design system's {@code .cl-page .cl-table .cl-table-check} rule
     * expects (44 px, a table cell) — the pick indicator was invisible. The cell now carries the class and holds a
     * presentational {@code cl-check-input} checkbox, reusing the existing checkbox styling instead of adding new
     * CSS for it.
     */
    @Test
    void theAddItemsPickIndicatorIsOnTheCellAndVisible() throws Exception {
        // given
        String script = read("src/main/resources/static/js/item-add-dialog.js");

        // then
        assertThat(script).contains("checkCell.className = 'cl-table-check'").contains("check.className = 'cl-check-input'")
                .contains("check.checked = !!entry").doesNotContain("check.className = 'cl-table-check'");
        assertThat(css()).contains(".cl-page .cl-table .cl-table-check {").contains(".cl-page .cl-table .cl-check-input {");
    }

    /**
     * Task 13 fix round 1 (Important 2 + Extra): the money formatter (NBSP thousands, U+2212 minus) used to be
     * copied verbatim into item-add-dialog.js from order-item-dialogs.js; now both call one shared
     * {@code window.CL_formatMoney}, which also adds the currency unit from a pattern the layout renders once
     * per page (data-cl-amount-format), so neither script builds the number or hard-codes "PLN"/"zł" itself.
     * Task 17's add-payment-dialog.js (fragments/add-payment-modal.html) joins the same rule.
     */
    @Test
    void theMoneyFormatterIsSharedNotDuplicated() throws Exception {
        // given
        String money = read("src/main/resources/static/js/money.js");
        String itemAdd = read("src/main/resources/static/js/item-add-dialog.js");
        String itemDialogs = read("src/main/resources/static/js/order-item-dialogs.js");
        String addPayment = read("src/main/resources/static/js/add-payment-dialog.js");
        String layout = read("src/main/resources/templates/layout.html");
        String itemFragment = read("src/main/resources/templates/fragments/item-add-modal.html");
        String paymentFragment = read("src/main/resources/templates/fragments/add-payment-modal.html");

        // then: the formatting body (NBSP grouping, minus sign) lives only in money.js
        assertThat(money).contains("window.CL_formatMoney = formatMoney").contains("data-cl-amount-format")
                .contains("'use strict'");
        for (String script : List.of(itemAdd, itemDialogs, addPayment)) {
            assertThat(script).contains("window.CL_formatMoney(").doesNotContain("\\B(?=(\\d{3})+(?!\\d))")
                    .doesNotContain("' PLN'").doesNotContain("' zł'");
        }

        // then: the layout renders the pattern once, and each fragment loads money.js before its own dialog script
        assertThat(layout).contains("data-cl-amount-format=#{general.currency.amount}");
        assertThat(itemFragment.indexOf("/js/money.js")).isNotNegative()
                .isLessThan(itemFragment.indexOf("/js/item-add-dialog.js"));
        assertThat(paymentFragment.indexOf("/js/money.js")).isNotNegative()
                .isLessThan(paymentFragment.indexOf("/js/add-payment-dialog.js"));
    }

    @Test
    void theShipmentsScriptAddsRemovesAndRenumbersRows() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-shipments.js");

        // then
        assertThat(script).contains("data-cl-shipment-template").contains("data-cl-shipment-remove")
                .contains("shipments[").contains("data-cl-carrier-select").contains("__other__").contains("'use strict'")
                .doesNotContain("innerHTML");
    }

    @Test
    void theDocumentsScriptFillsTheIssueDialogFromTheMenuEntry() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-documents.js");

        // then
        assertThat(script).contains("cl:dialog-open").contains("data-document-type").contains("documentType")
                .contains("'use strict'").doesNotContain("innerHTML").doesNotContain("confirm(");
        // the title is one translated sentence with a {type} slot, not a fixed prefix followed by the type
        assertThat(script).contains("data-template").contains("replace('{type}'");
    }

    @Test
    void removingAPaymentRowClearsWhatMakesItCompleteAndHidesIt() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-payments.js");

        // then (Payment.isComplete: a reference, a non-zero amount or a fee keeps the row)
        assertThat(script).contains("data-cl-payment-remove").contains(".amount").contains(".fee").contains(".referenceNo")
                .contains("hidden").contains("'use strict'").doesNotContain("innerHTML");
    }

    /**
     * The closing strip (spec §4.2) restyles the list's doc marks inline, as full sentences: both glyph and tone
     * carry the meaning, and the side column never drops below 320 px at its widest breakpoint (spec §4).
     * <p>
     * Review fix round 1: the inline mark must not inherit the list's pill chrome (padding, fixed line-height,
     * {@code white-space: nowrap}) or the list's {@code .is-todo} background — the tone belongs on the icon only,
     * so the sentence itself can wrap and does not carry a second warn-soft pill behind it. The side-column check
     * is scoped to the {@code .cl-layout-aside} rule bodies only: the add-items dialog's own, unrelated
     * {@code .cl-item-add-scroll} height legitimately keeps 340 px (Task 4 review round 1).
     */
    @Test
    void theClosingStripStylesTheInlineMarksWithBothGlyphAndTone() throws Exception {
        // given
        String css = css();

        // then
        assertThat(css).contains(".cl-page .cl-closing {")
                .contains(".cl-page .cl-doc-marks.is-inline .is-todo .cl-doc-mark-icon { color: var(--cl-warn); background: var(--cl-warn-soft); }")
                .contains(".cl-page .cl-doc-marks.is-inline .cl-doc-mark { display: inline-flex; align-items: center; gap: 6px; font-size: 13.5px; height: auto; background: none; padding: 0; white-space: normal; font-weight: 400; letter-spacing: 0; }");

        String aside = rule(css, ".cl-page .cl-layout-aside");
        assertThat(aside).contains("320px").doesNotContain("340px");
    }

    @Test
    void theE2eFixesKeepTheDetailsLayoutTight() throws Exception {
        // given
        String css = css();

        // then: the review todo reads like the other todos, the dialog form lines up with its title, the strip keeps a
        // gap to the next card on phones, and card-mode rows do not indent the name by the list's checkbox padding
        assertThat(rule(css, ".cl-page .cl-doc-marks.is-inline .cl-doc-mark .cl-link-button"))
                .contains("text-decoration: underline").contains("font-weight: 400").contains("color: inherit");
        assertThat(rule(css, ".cl-page .cl-dialog-body .cl-card-grid")).contains("padding: 4px 0 0");
        assertThat(css).contains("@media (max-width: 1023px) { .cl-page .cl-closing { margin-bottom: 16px; } }")
                .contains(".cl-page .cl-table.is-wrap tbody tr > th.cl-table-key { padding-left: 0; padding-right: 0; }")
                .contains(".cl-page .cl-table.is-wrap tbody tr:not(.cl-table-group):not(:has(.cl-table-check)) > td.cl-table-actions { grid-column: 2; grid-row: 1; }");
        assertThat(rule(css, ".cl-page .cl-help.is-note")).contains("font-size: 13px");
    }

    @Test
    void everyCssBlockIsClosedSoNoLaterRuleEndsUpInsideAMediaQuery() throws Exception {
        // given: two missing braces once swallowed every later rule (the closing strip, the side layout) into a
        // prefers-reduced-motion block, so the browser ignored them on ordinary screens
        String css = css().replaceAll("(?s)/\\*.*?\\*/", "");

        // when
        int depth = 0;
        int minimum = 0;
        for (char c : css.toCharArray()) {
            depth += c == '{' ? 1 : c == '}' ? -1 : 0;
            minimum = Math.min(minimum, depth);
        }

        // then
        assertThat(depth).isZero();
        assertThat(minimum).isZero();
    }
}
