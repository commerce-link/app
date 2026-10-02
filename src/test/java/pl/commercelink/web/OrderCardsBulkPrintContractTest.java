package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Printing several order cards from the orders list: the style and script contracts the feature relies on. */
class OrderCardsBulkPrintContractTest {

    static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    static final String TABLE_SELECT = "src/main/resources/static/js/table-select.js";
    static final String PRINT = "src/main/resources/static/js/print.js";

    static String folded(String path) throws Exception {
        return read(path).replaceAll("\\s+", " ");
    }

    static String css() throws Exception {
        return read("src/main/resources/static/css/commercelink.css");
    }

    /** The declarations of every rule of the style sheet whose selector list is exactly {@code selector}, joined. */
    static String rule(String css, String selector) {
        StringBuilder body = new StringBuilder();
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(selector) + " \\{([^}]*)}").matcher(css);
        while (matcher.find()) {
            body.append(matcher.group(1));
        }
        return body.toString();
    }

    /**
     * The printed QR code and the one-column details hang off each card's own box, so every card of a batch has the
     * code at its own top-right corner. The declarations are the ones the page body carried, and the single card's box
     * is the same as the page body's, so the single card prints exactly as before.
     */
    @Test
    void theQrCodeAndTheOneColumnDetailsBelongToEachCard() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card:has(.cl-print-qr)")).contains("position: relative;");
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card:has(.cl-print-qr) dl.cl-kv.cl-print-meta"))
                .contains("grid-template-columns: minmax(0, 1fr);").contains("padding-right: 34mm;")
                .contains("align-content: start;");
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card:has(.cl-print-qr) .cl-kv.cl-print-meta dd"))
                .contains("overflow-wrap: break-word;");
        assertThat(rule(css, ".cl-page.cl-print-page .cl-page-header-row div.cl-print-qr"))
                .contains("position: absolute;").contains("top: 0;").contains("right: 0;").contains("width: 30mm;");
        assertThat(css).doesNotContain(".cl-page-body:has(.cl-print-qr)");
        assertThat(rule(css, ".cl-page .cl-print-card")).isEmpty();
    }

    /** A forced break keeps the margin after it, so the screen gap between cards is dropped on paper. */
    @Test
    void everyCardAfterTheFirstStartsOnANewPage() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page.cl-print-page .cl-print-card + .cl-print-card"))
                .contains("break-before: page;").contains("margin-top: 0;");
        assertThat(rule(css, ".cl-page .cl-print-card + .cl-print-card")).contains("margin-top: 40px;");
    }

    /** The row link's ::after covers the whole row; the checkbox cell must lie above it, or a tick opens the order. */
    @Test
    void theCheckboxCellSitsAboveTheRowLink() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page .cl-table.is-orders .cl-table-check")).contains("position: relative;").contains("z-index: 1;");
    }

    /** table-select.js marks the table .is-selectable; without it (no JavaScript) the orders table has no empty column. */
    @Test
    void withoutTheScriptTheOrdersTableHasNoCheckboxColumn() throws Exception {
        // given
        String css = css();

        // then
        assertThat(rule(css, ".cl-page .cl-table.is-orders:not(.is-selectable) .cl-table-check")).contains("display: none;");
    }

    /**
     * Card mode has no selection, and the generic card-mode rules of selectable tables
     * (`.cl-page .cl-table[data-cl-select-table] tbody th.cl-table-key`, specificity 0,4,2) must not reach the orders
     * card (its own rules are 0,3,2): these overrides outrank both.
     */
    @Test
    void theCardModeHasNeitherCheckboxesNorTheSelectionRow() throws Exception {
        // given
        String css = css();
        String phone = css.substring(css.indexOf("/* Card mode (< 720 px) has no selection"));

        // then
        assertThat(rule(phone, ".cl-page .cl-table.is-orders[data-cl-select-table] .cl-table-check")).contains("display: none;");
        assertThat(rule(phone, ".cl-page .cl-table.is-orders[data-cl-select-table] tbody th.cl-table-key"))
                .contains("display: contents;").contains("padding: 0;");
        assertThat(rule(phone, ".cl-page .cl-table.is-orders tr.is-selected")).contains("background: transparent;");
        assertThat(rule(phone, ".cl-page .cl-selection-row.is-wide-only")).contains("display: none;");
        // the rules above sit in the phone media query right under that comment
        assertThat(phone.indexOf("@media screen and (max-width: 719px) {")).isBetween(0, 500);
    }

    /** The selection row hands the checked orders, in the rows' order, to print.js's hidden frame. */
    @Test
    void theSelectionRowPrintsTheCheckedOrdersThroughThePrintFrame() throws Exception {
        // given
        String select = folded(TABLE_SELECT);
        String print = folded(PRINT);

        // then
        assertThat(print).contains("window.CL_printInFrame = function (href, opener) { printInFrame(href, opener); };");
        assertThat(select).contains("event.target.closest('[data-cl-select-print]')")
                .contains("var print = window.CL_printInFrame;")
                .contains("params.append('ids', box.value);")
                .contains("button.getAttribute('data-cl-select-print')")
                .contains("parseInt(button.getAttribute('data-cl-select-confirm-above'), 10)")
                .contains("if (isNaN(limit) || rows.length <= limit) { print(href, button); return; }");
    }

    /** Above the limit the page's dialog asks first, in the Polish plural form PluralForm.java would pick. */
    @Test
    void theConfirmationUsesThePluralFormOfPluralFormJava() throws Exception {
        // given
        String select = folded(TABLE_SELECT);
        String java = folded("src/main/java/pl/commercelink/web/orders/PluralForm.java");

        // then
        assertThat(java).contains("lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)");
        assertThat(select).contains("var ones = count % 10; var tens = count % 100;")
                .contains("return ones >= 2 && ones <= 4 && (tens < 12 || tens > 14) ? 'few' : 'many';")
                .contains("'data-cl-select-confirm-title-' + form, 'data-cl-select-confirm-message', 'data-cl-select-confirm-action-' + form")
                .contains("accept.textContent = (button.getAttribute(actionAttr) || accept.textContent).replace('{n}', count);");
    }

    /**
     * Tables are set up again after every list reload, so a resize listener per table would leak one per reload (each
     * holding a detached table): there is one listener for the whole page, outside init, over the tables in the document.
     */
    @Test
    void theSelectionRowsAreMeasuredOnResizeByOneListenerOutsideInit() throws Exception {
        // given
        String select = read(TABLE_SELECT);
        String init = select.substring(select.indexOf("function init(table)"), select.indexOf("// One listener for the page"));

        // then
        assertThat(Pattern.compile(Pattern.quote("addEventListener('resize'")).matcher(select).results().count()).isEqualTo(1);
        assertThat(init).doesNotContain("'resize'");
        assertThat(folded(TABLE_SELECT)).contains("querySelectorAll('table[data-cl-select-ready]')");
    }

    /**
     * Without JavaScript the check column is hidden, so the first visible column takes the 20 px edge padding the
     * first-child rule would have given the check cell. Only from 720 px: card mode has its own layout.
     */
    @Test
    void withoutTheScriptTheFirstVisibleColumnKeepsTheEdgeIndent() throws Exception {
        // given
        String css = css();
        String wide = css.substring(css.indexOf("@media screen and (min-width: 1024px) {\n    .cl-page .cl-table.is-orders:not(.is-selectable) .cl-table-check + :is(th, td)") );

        // then
        assertThat(rule(wide, ".cl-page .cl-table.is-orders:not(.is-selectable) .cl-table-check + :is(th, td)"))
                .contains("padding-left: 20px;");
    }

    /** list-page.js swaps the results block: the fresh table is set up again, a table already set up is left alone. */
    @Test
    void aSwappedListIsSetUpAgainOnlyOnce() throws Exception {
        // given
        String select = folded(TABLE_SELECT);

        // then
        assertThat(select).contains("document.addEventListener('cl-list:swapped', initAll);")
                .contains("if (table.hasAttribute('data-cl-select-ready')) { return; } table.setAttribute('data-cl-select-ready', '');")
                .contains("table.classList.add('is-selectable');");
    }

    /** "Clear" unticks everything; texts that carry the count follow it. */
    @Test
    void clearAndTheCountedTextsFollowTheSelection() throws Exception {
        // given
        String select = folded(TABLE_SELECT);

        // then
        assertThat(select).contains("event.target.closest('[data-cl-select-clear]')")
                .contains("function clearSelection(table) {")
                .contains("bar.querySelectorAll('[data-cl-selection-text]')")
                .doesNotContain("alert(").doesNotContain("confirm(").doesNotContain("innerHTML");
    }
}
