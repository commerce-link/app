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
}
