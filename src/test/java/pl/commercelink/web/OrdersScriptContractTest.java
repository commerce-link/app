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
    void theShipmentsCardPollsTheCancellationStateAndReloadsOnceFurgonetkaAnswers() throws Exception {
        // given
        String script = read("src/main/resources/static/js/shipment-cancellation.js");

        // then: every 5 s for about 2 minutes, a reload only once the answer is final and no dialog, open menu or
        // row selection would be lost; a lost request is not an answer
        assertThat(script).contains("'use strict'").contains("[data-cl-cancellation-poll]")
                .contains("5000").contains("120000").contains("body.inProgress === false")
                .contains("window.location.reload()").contains("dialog[open]")
                .contains("details.cl-menu[open]").contains("[data-cl-select-row]:checked").contains("'X-Requested-With': 'fetch'")
                .contains("opaqueredirect")
                .doesNotContain("innerHTML").doesNotContain("style.").doesNotContain("setInterval");
    }

    @Test
    void printFrameIsRemovedOnlyOnAfterprintPagehideOrTheNextPrint() throws Exception {
        // given: the script with its whitespace folded, so formatting does not matter
        String print = read("src/main/resources/static/js/print.js").replaceAll("\\s+", " ");
        java.util.regex.Matcher timers = java.util.regex.Pattern
                .compile("setTimeout\\(\\s?function \\(\\) \\{([^}]*)\\}, ([^)]+)\\)").matcher(print);

        // then: no timer takes the sheet away (Firefox may still show its preview); a deferral to the end of the
        // current task (delay 0) is the only timer allowed to remove the frame
        while (timers.find()) {
            if (timers.group(1).contains("remove(")) {
                assertThat(timers.group(2).trim()).as("delay of a timer that removes the frame").isEqualTo("0");
            }
        }
        assertThat(print).containsPattern("addEventListener\\('afterprint', function \\(\\) \\{[^;]*setTimeout\\(function \\(\\) \\{ remove\\(frame\\);")
                .containsPattern("addEventListener\\('pagehide', function \\(\\) \\{ if \\(current\\) \\{ remove\\(current\\);")
                .containsPattern("function printInFrame\\([^)]*\\) \\{[^{]*if \\(current\\) \\{ remove\\(current\\);");
        // and the keyboard goes back to the menu's summary once the frame that held the focus is gone
        assertThat(print).containsPattern("function remove\\(frame\\) \\{.*restoreFocus\\(\\);")
                .contains(":scope > summary")
                .containsPattern("active === document.body")
                .contains("target.focus()");
    }
}
