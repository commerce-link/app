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
                ".cl-page .cl-card-toggle {", ".cl-page .cl-card-grid {", ".cl-page.cl-dialog-host {",
                ".cl-page .cl-note {", ".cl-page .cl-note-panel {", ".cl-page .cl-table .cl-table-marks {")) {
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
        // the menus are native details: the script enhances them and never hides the list itself
        assertThat(menu).contains("details.cl-menu").contains("'toggle'").contains(":scope > summary")
                .contains("Escape").contains("ArrowDown").contains("aria-disabled").contains("is-up").contains(".open = false")
                .doesNotContain(".hidden =").doesNotContain("aria-controls");
        // a marker's popover (details.cl-note) shares the menus' one-open rule, Escape and outside click, and flips
        assertThat(menu).contains("details.cl-note").contains(".cl-note-panel").contains("is-end");
        assertThat(dialog).contains("data-cl-dialog-open").contains("data-cl-dialog-close").contains("showModal")
                .contains("cl:dialog-open").contains("details.cl-menu").contains(":scope > summary");
        assertThat(collapse).contains("data-cl-collapse").contains("cl-card-toggle").contains("(max-width: 719px)");
        assertThat(copy).contains("cl-copy-inline").contains("data-cl-copy").contains("CL_TOAST_COPIED");
        for (String script : List.of(menu, dialog, collapse, copy)) {
            assertThat(script).contains("'use strict'").doesNotContain("alert(").doesNotContain("confirm(");
        }
    }

    /**
     * A card marked data-cl-collapse re-enhances on cl:form-replaced (the order settings
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
     * The add-items pick indicator maps to cl-table-check; the class once landed on a decorative span instead of
     * the cell the design system's {@code .cl-page .cl-table .cl-table-check} rule
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
     * The money formatter (NBSP thousands, U+2212 minus) used to be
     * copied verbatim into item-add-dialog.js from order-item-dialogs.js; now both call one shared
     * {@code window.CL_formatMoney}, which also adds the currency unit from a pattern the layout renders once
     * per page (data-cl-amount-format), so neither script builds the number or hard-codes "PLN"/"zł" itself.
     * The add-payment-dialog.js (fragments/add-payment-modal.html) follows the same rule.
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
    void theShipmentsScriptOnlyPicksTheCarrierAndLeavesTheGroupsToRepeatFields() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-shipments.js");
        String page = read("src/main/resources/templates/orders/details.html");

        // then: adding, removing and renumbering the shipment groups is repeat-fields.js, loaded before this script
        assertThat(script).contains("data-cl-carrier-select").contains("__other__").contains("cl:repeat-added")
                .contains("'use strict'").doesNotContain("shipments[").doesNotContain("innerHTML");
        assertThat(page.indexOf("/js/repeat-fields.js")).isNotNegative().isLessThan(page.indexOf("/js/order-shipments.js"));
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
     * The side column never drops below 320 px at its widest breakpoint. The check is scoped to the
     * {@code .cl-layout-aside} rule bodies only: the add-items dialog's own, unrelated {@code .cl-item-add-scroll}
     * height legitimately keeps 340 px. The closing strip is gone, and with it every rule only it used; the list's
     * doc marks stay.
     */
    @Test
    void theSideColumnKeepsItsWidthAndTheClosingStripRulesAreGone() throws Exception {
        // given
        String css = css();

        // then
        assertThat(css).doesNotContain(".cl-closing").doesNotContain(".cl-doc-marks.is-sentences")
                .contains(".cl-page .cl-doc-marks {").contains(".cl-page .cl-doc-mark.is-todo {");
        String aside = rule(css, ".cl-page .cl-layout-aside");
        assertThat(aside).contains("320px").doesNotContain("340px");
    }

    @Test
    void theCopyIconIsAlwaysShownAndEveryCopyButtonReachesItsTargetSizeWithoutGrowingTheLine() throws Exception {
        // given
        String css = css();
        String copyRules = css.substring(css.indexOf("/* Copy inline:"), css.indexOf("/* Record header with status"))
                .replaceAll("(?s)/\\*.*?\\*/", "");

        // then: nothing hides the icon; it is muted and takes the link colour on hover and focus
        assertThat(css).doesNotContain("cl-copy-icon {\n    opacity").doesNotContain(".cl-copy-inline.is-pinned")
                .doesNotContain(".cl-copy-pair");
        assertThat(copyRules).doesNotContain("opacity").doesNotContain("position: absolute");
        assertThat(rule(css, ".cl-page .cl-copy-inline .cl-copy-icon")).contains("color: var(--cl-ink-3)");
        assertThat(rule(css, ".cl-page .cl-copy-inline:hover .cl-copy-icon,\n.cl-page .cl-copy-inline:focus-visible .cl-copy-icon"))
                .contains("color: var(--cl-accent-ink)");
        // 24 px on desktop: padding pulled back by an equal negative margin; icon-only buttons grow to the right
        assertThat(rule(css, ".cl-page .cl-copy-inline")).contains("padding: 3px 0").contains("margin: -3px 0");
        assertThat(rule(css, ".cl-page .cl-copy-inline.is-icon")).contains("padding-right: 10px").contains("margin-right: -10px");
        // the button is positioned (its area lies over plain text); its own content and every control around it sit
        // one level higher, below the sticky selection row (5), the top bar and the popovers
        assertThat(rule(css, ".cl-page .cl-copy-inline")).contains("position: relative");
        assertThat(css).contains(".cl-page .cl-copy-inline > *,\n.cl-page :is(.cl-table, .cl-record-title, .cl-record-meta, "
                + ".cl-address):has(.cl-copy-inline)\n        :is(a, summary, label, input, button:not(.cl-copy-inline)) {\n"
                + "    position: relative;\n    z-index: 1;\n}");
        assertThat(copyRules.split("z-index", -1)).hasSize(2);
        // the ring marks what is seen, not the hit area
        assertThat(rule(css, ".cl-page .cl-copy-inline:focus-visible > :is(.cl-copy-code, .cl-copy-icon)"))
                .contains("outline: 2px solid var(--cl-accent)");
        // 44 px below 1024 px
        String touch = media(css.substring(css.indexOf("@media screen and (max-width: 1023px) {\n    .cl-page .cl-button.is-icon")),
                "@media screen and (max-width: 1023px)");
        assertThat(touch).contains(".cl-page .cl-copy-inline {\n        padding-top: 13px;\n        padding-bottom: 13px;\n"
                        + "        margin-top: -13px;\n        margin-bottom: -13px;")
                .contains(".cl-page .cl-copy-inline.is-icon {\n        padding-right: 30px;\n        margin-right: -30px;")
                .contains(".cl-page .cl-address .cl-copy-inline {");
    }

    @Test
    void theE2eFixesKeepTheDetailsLayoutTight() throws Exception {
        // given
        String css = css();

        // then: the dialog form lines up with its title, and card-mode rows do not indent the name by the list's
        // checkbox padding
        assertThat(rule(css, ".cl-page .cl-dialog-body .cl-card-grid")).contains("padding: 4px 0 0");
        assertThat(css).contains(".cl-page .cl-table.is-wrap tbody tr > th.cl-table-key { padding-left: 0; padding-right: 0; }")
                .contains(".cl-page .cl-table.is-wrap tbody tr:not(.cl-table-group):not(:has(.cl-table-check)) > td.cl-table-actions { grid-column: 2; grid-row: 1; }");
        assertThat(rule(css, ".cl-page .cl-help.is-note")).contains("font-size: 13px");
    }

    /** The body of the first {@code @media} block that starts with {@code query}. */
    private static String media(String css, String query) {
        int start = css.indexOf(query + " {");
        assertThat(start).as(query).isNotNegative();
        int depth = 0;
        for (int i = css.indexOf('{', start); i < css.length(); i++) {
            depth += css.charAt(i) == '{' ? 1 : css.charAt(i) == '}' ? -1 : 0;
            if (depth == 0) {
                return css.substring(start, i + 1);
            }
        }
        throw new IllegalStateException("unclosed " + query);
    }

    @Test
    void noComponentModifierCollidesWithBulmasDisplayHelpers() throws Exception {
        // given: Bulma's .is-inline / .is-block / .is-flex set display with !important, so a cl-* rule never wins
        String css = css();
        String templates = read("src/main/resources/templates/fragments/item-add-modal.html");

        // then
        assertThat(css).doesNotContainPattern("\\.cl-[a-z-]+\\.is-(inline|block|flex)\\b");
        assertThat(templates).doesNotContainPattern("class=\"cl-[a-z-]+ is-(inline|block|flex)\\b");
    }

    @Test
    void theActionMenuIsBorderBox() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page .cl-menu,\n.cl-page .cl-menu *")).contains("box-sizing: border-box");
    }

    @Test
    void theOrderCommentTakesTheWholeLineAndKeepsItsLineBreaks() throws Exception {
        // given
        String css = css();

        // then: the phone rule for ".cl-kv.is-column > div" is less specific than the ".cl-kv-wide" one
        assertThat(rule(css, ".cl-page .cl-kv.is-column > div.cl-kv-wide")).contains("grid-template-columns: minmax(0, 1fr)");
        assertThat(rule(css, ".cl-page .cl-kv.is-column dd.cl-kv-text")).contains("white-space: pre-line")
                .contains("text-align: left");
        assertThat(css).doesNotContain(".cl-kv-group");
        assertThat(rule(css, ".cl-page .cl-disclosure-body > dl.cl-kv.is-column")).contains("padding: 0");
    }

    @Test
    void aFormDialogHasOneExplicitColumnSoNothingScrollsSideways() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page .cl-dialog.is-form .cl-dialog-body")).contains("grid-template-columns: minmax(0, 1fr)");
        assertThat(css).containsPattern("@media screen and \\(max-width: 719px\\) \\{\\s+\\.cl-page \\.cl-segmented\\.is-fit \\{\\s+width: 100%;");
        assertThat(rule(css, ".cl-page .cl-segmented.is-fit > *")).contains("flex: 1 1 auto");
    }

    @Test
    void theSideColumnStartsAt1366AndPhonesFollowTheDom() throws Exception {
        // given
        String css = css();

        // when
        String below1366 = media(css, "@media screen and (max-width: 1365px)");

        // then
        assertThat(below1366).contains(".cl-page .cl-layout-aside {").contains("grid-template-columns: minmax(0, 1fr)");
        assertThat(rule(css, ".cl-page .cl-layout-main,\n.cl-page .cl-layout-side")).doesNotContain("display: contents");
        assertThat(css).doesNotContain("[data-order=")
                .doesNotContain("minmax(0, 1fr) 300px").doesNotContain("\"closing").doesNotContain(".cl-layout-full");
        assertThat(rule(css, ".cl-page .cl-layout-aside")).contains("320px");
        // same specificity as the column rules, so it must come after them or "grid-area: main" wins and the cards overlap
        assertThat(css.indexOf("@media screen and (max-width: 1365px)"))
                .isGreaterThan(css.indexOf("\n.cl-page .cl-layout-main {"))
                .isGreaterThan(css.indexOf("\n.cl-page .cl-layout-side {"));
        String[] blocks = css.split("@media screen and \\(max-width: 1023px\\)");
        for (int i = 1; i < blocks.length; i++) {
            String body = blocks[i].substring(0, Math.max(blocks[i].indexOf("\n}\n"), 0));
            assertThat(body).doesNotContainPattern("[\\s;{]order:");
        }
    }

    @Test
    void theItemsScriptGreysAnActionNoCheckedItemFitsWithItsSkippedReasonAndKeepsTheServersReason() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-items.js");

        // then
        assertThat(script).contains("button.setAttribute('aria-disabled', 'true')").contains("button.hasAttribute('data-cl-bulk-unavailable')")
                .contains("button.getAttribute('data-skipped')").contains("reason && !unavailable")
                .contains("button.setAttribute('aria-describedby', reason.id)").contains("button.removeAttribute('aria-describedby')")
                .contains("form.addEventListener('change', refresh)").doesNotContain("button.disabled");
    }

    @Test
    void theSelectionRowTakesTheHeadersPlaceWithoutMovingTheRows() throws Exception {
        // given
        String select = read("src/main/resources/static/js/table-select.js");
        String css = css();

        // then: the header keeps its place with its contents hidden and the row lies over it — as tall as the measured
        // header, pulling the table up by the same amount — the same in Chromium and WebKit (no visibility: collapse,
        // which WebKit leaves as an empty gap); the row sticks flush under the top bar and follows the markup's order
        assertThat(select).contains("table.classList.toggle('has-selection', selected.length > 0)")
                .contains("bar.style.setProperty('--cl-selection-head'").contains("replace('{k}', String(selected.length))")
                .contains("replace('{n}', String(shown.length))").contains("function keepFocus(table)");
        assertThat(rule(css, ".cl-page .cl-table.has-selection > thead")).contains("visibility: hidden;");
        assertThat(rule(css, ".cl-page .cl-selection-row")).contains("position: sticky;")
                .contains("top: var(--cl-topbar-height);").contains("min-height: var(--cl-selection-head, 40px);")
                .contains("margin-bottom: calc(-1 * var(--cl-selection-head, 40px));")
                .contains("background: var(--cl-surface-2);");
        assertThat(css).doesNotContain(".cl-selection-bar");
    }

    @Test
    void theScopeSuffixAppearsOnlyWhenSomeSelectedItemsDoNotFit() throws Exception {
        // given
        String script = read("src/main/resources/static/js/order-items.js");

        // then
        assertThat(script).contains("fits === rows.length ? label").contains("data-cl-scope-count-template")
                .contains("replace('{k}', String(fits))").contains("window.CL_confirmBulk(button, count,")
                .doesNotContain("({n}/{m})");
    }

    @Test
    void deadRulesOfAbandonedComponentsAreGone() throws Exception {
        // given
        String css = css();

        // then: the orders list's overdue note takes its colour and weight from .cl-due-note.is-bad
        assertThat(css).doesNotContain(".cl-menu-check").doesNotContain("ol.cl-stepper").doesNotContain("ul.cl-checklist")
                .doesNotContain(".cl-table-order").doesNotContain(".cl-page .cl-table .cl-table-sub.is-bad {");
    }

    @Test
    void theDetailsFollowTheDesignSystemScale() throws Exception {
        // given
        String css = css();

        // then
        assertThat(css).doesNotContain(".cl-page .cl-record-title .cl-status {");
        assertThat(rule(css, ".cl-page .cl-record-title")).contains("gap: 8px 12px");
        assertThat(rule(css, ".cl-page .cl-record-meta")).contains("font-size: 14px;");
        assertThat(rule(css, ".cl-page .cl-record-meta.is-secondary")).contains("font-size: 13px;");
        // the uppercase label is .cl-eyebrow; the layout classes keep only their spacing
        for (String selector : List.of(".cl-page .cl-address-head", ".cl-page .cl-table tbody tr.cl-table-group > th",
                ".cl-page .cl-item-add-basket-head")) {
            assertThat(rule(css, selector)).as(selector).doesNotContain("text-transform").doesNotContain("letter-spacing")
                    .doesNotContain("font-size");
        }
        String wide = media(css.substring(css.indexOf("/* The items table is not a dense dialog table")),
                "@media screen and (min-width: 720px)");
        assertThat(wide).contains(".cl-page .cl-table.is-wrap :is(th, td):first-child {").contains("padding-left: 20px")
                .contains("padding-right: 20px");
        assertThat(rule(css, ".cl-page .cl-menu-glyph")).contains("font-size: 18px");
    }

    @Test
    void theAddItemsCardsNameTheirColumnsAndTouchTargetsAreLargeEnough() throws Exception {
        // given
        String css = css();
        String script = read("src/main/resources/static/js/item-add-dialog.js");

        // then
        assertThat(script).contains("cell.dataset.label").contains(".cl-item-add-table thead th")
                .contains("nameCell.className = 'cl-item-add-name'");
        assertThat(rule(css, ".cl-page .cl-item-add-table td:is(.cl-table-check, .cl-item-add-name)"))
                .contains("grid-template-columns: minmax(0, 1fr)");
        String touch = media(css.substring(css.indexOf("@media screen and (max-width: 1023px) {\n    .cl-page .cl-button.is-icon")),
                "@media screen and (max-width: 1023px)");
        assertThat(touch).contains(".cl-page .cl-address a {").contains("min-height: 44px")
                .contains(".cl-page .cl-item-add-table .cl-table-sort {");
        assertThat(rule(css, ".cl-page .cl-selection-actions.is-static")).contains("padding: 12px 20px 16px");
        assertThat(rule(css, ".cl-page .cl-input.is-reference")).contains("width: 22ch");
    }

    @Test
    void everyCssBlockIsClosedSoNoLaterRuleEndsUpInsideAMediaQuery() throws Exception {
        // given: two missing braces once swallowed every later rule (the side layout among them) into a
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

    @Test
    void theTimelineScriptHidesOnlyTheExtraEventsAndKeepsTheLineUnbroken() throws Exception {
        // given
        String timeline = read("src/main/resources/static/js/timeline.js");
        String css = css();

        // then: the extra events are hidden inside the same list, the focus moves to the button that is left and the
        // change is announced
        assertThat(timeline).contains("'use strict'").contains("data-cl-timeline-limit").contains("data-cl-timeline-more")
                .contains("data-cl-timeline-toggle=\"expand\"").contains("data-cl-timeline-toggle=\"collapse\"")
                .contains("aria-controls").contains("data-cl-timeline-status")
                .contains("(expanded ? collapse : expand).focus()").contains("more.hidden = false")
                .doesNotContain("style.").doesNotContain("innerHTML");
        // collapsed, the last visible event carries the line on, dashed, down to the node; the node sits on the axis, larger than a dot
        assertThat(rule(css, ".cl-page .cl-timeline-item:has(+ .cl-timeline-item[hidden])::after"))
                .contains("bottom: -13px").contains("repeating-linear-gradient(to bottom, var(--cl-line)");
        assertThat(rule(css, ".cl-page .cl-timeline-item:has(+ .cl-timeline-item[hidden])")).contains("padding-bottom: 0");
        assertThat(rule(css, ".cl-page .cl-timeline-more")).contains("padding: 0 20px 8px");
        assertThat(rule(css, ".cl-page .cl-timeline-node-dot")).contains("width: 18px").contains("margin: 13px 0 0 -2px")
                .contains("border: 2px solid var(--cl-line)");
        assertThat(rule(css, ".cl-page .cl-timeline-less")).contains("margin-left: 16px");
        // the line reaches the next dot's ring instead of stopping short of it
        assertThat(rule(css, ".cl-page .cl-timeline-item::after")).contains("bottom: -5px");
    }
}
