package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Layout rules of the warehouse list table that the browser checks (E2E, Task 11) found missing. */
class WarehouseListStylesContractTest {

    private static String section() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int start = css.indexOf("/* --- Warehouse list");
        assertThat(start).as("section marker").isNotNegative();
        int end = css.indexOf("/* ---", start + 10);
        return css.substring(start, end < 0 ? css.length() : end);
    }

    @Test
    void statusColumnIsWideEnoughForTheLongestStatusPill() throws Exception {
        // given
        String section = section();

        // when / then
        // "Zarezerwowane" is a 114 px pill; 16 % of the table is narrower than that below 1366 px and the text ran out of it
        // 9.5rem = the 114 px pill plus the cell's 2 × 12 px padding; 8.5rem (wave 2) left 112 px and cut "Zarezerwowane"
        assertThat(section).contains(".cl-page .cl-table.is-warehouse .cl-col-status { width: 9.5rem; }")
                .doesNotContain(".cl-col-status { width: 16%; }");
    }

    @Test
    void categoryCellBreaksALongWordWithoutHyphenationAndTheSerialNumberStaysOneToken() throws Exception {
        // given
        String section = section();

        // when / then
        // hyphens: auto used Polish rules on English words ("Headpho-nes"); overflow-wrap still keeps a long word in its cell
        assertThat(section).doesNotContain("hyphens").contains("overflow-wrap: break-word;");
        String table = section.substring(section.indexOf("@media screen and (min-width: 720px) {"));
        assertThat(table).contains(".cl-page .cl-table.is-warehouse .cl-cell-serial { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }");
    }

    @Test
    void infoPillOfASelectedRowStandsOutFromTheRowTint() throws Exception {
        // given
        String section = section();

        // when / then
        // --cl-info-soft is the same colour as --cl-accent-soft, the tint of a checked row
        assertThat(section).contains(".cl-page .cl-table.is-warehouse tr.is-selected .cl-status.is-info { background: var(--cl-surface); box-shadow: inset 0 0 0 1px var(--cl-accent); }");
    }

    @Test
    void dockedSelectionRowIsTheSharedComponentOfTheDeliveryDetailsBelowTheDrawerScrim() throws Exception {
        // given
        String section = section();

        // when / then
        // Ruling 14: the block of PR #259 verbatim, so a merge of both branches leaves one identical block; z-index 15 sits
        // below the drawer's scrim (--cl-z-scrim 20), and the room under the list is the measured --cl-docked-bar
        assertThat(section).contains("""
                    .cl-page .cl-selection-row.is-docked.is-in-view:not([hidden]) {
                        position: fixed;
                        top: auto;
                        right: 0;
                        bottom: 0;
                        left: 0;
                        z-index: 15;
                        margin: 0;
                        padding: 4px 16px calc(4px + env(safe-area-inset-bottom));
                        border-top: 1px solid var(--cl-line);
                        border-bottom: 0;
                        box-shadow: 0 -6px 16px rgba(14, 23, 33, .12);
                    }
                """).contains("""
                    .cl-page .cl-selection-host:has(> .cl-selection-row.is-docked.is-in-view:not([hidden])) {
                        padding-bottom: var(--cl-docked-bar, 112px);
                    }
                """);
        assertThat(section).doesNotContain("z-index: 30").doesNotContain(":has(.cl-selection-row:not([hidden]))")
                .doesNotContain("rgba(15, 23, 42");
    }

    @Test
    void mutedTextOfASelectedWarehouseRowKeepsAAContrastOnTheTint() throws Exception {
        // given
        String section = section();

        // when / then
        // --cl-ink-3 is 4.04:1 on --cl-accent-soft ("Bez kategorii" of a checked row)
        assertThat(section).contains(".cl-page .cl-table.is-warehouse tr.is-selected .cl-muted { color: var(--cl-ink-2); }");
    }

    @Test
    void selectedUnitsHaveNoMarginOfTheirOwn() throws Exception {
        // given
        String section = section();

        // when / then
        assertThat(section).doesNotContain("[data-cl-selection-units] { margin-left");
    }

    @Test
    void resultsLineStaysAtTheRightEdgeWithOrWithoutChips() throws Exception {
        // given
        String section = section();

        // when / then
        // alone in its flex row (no chips) the line fell to the left edge, next to chips it stood on the right
        assertThat(section).contains(".cl-page .cl-list-meta.is-warehouse .cl-table-results { margin-left: auto; text-align: right; }");
    }

    @Test
    void quantityTableStaysATwoColumnTableOnPhones() throws Exception {
        // given
        String section = section();

        // when / then
        // the shared card mode left an empty label column and pushed the name and the field to the right 55 %
        assertThat(section).contains(".cl-page .cl-dialog .cl-table.cl-quantity-table { display: table; }")
                .contains(".cl-page .cl-dialog .cl-quantity-table tr { display: table-row; padding: 0; border-top: 0; }")
                .contains(".cl-page .cl-dialog .cl-quantity-table :is(tbody th, td)::before { content: none; }");
    }

    @Test
    void codesStayOnOneLineFromTableWidthThePillFitsItsCellAndTheCommentShowsTwoLines() throws Exception {
        // given
        String section = section();

        // when / then
        String table = section.substring(section.indexOf("@media screen and (min-width: 720px) {"));
        assertThat(table).contains(".cl-page .cl-table.is-warehouse .cl-table-code { white-space: nowrap;")
                .contains(".cl-page .cl-table.is-warehouse .cl-col-cost { width: 17%; }");
        // the pill may break after "syst." (the amount itself is grouped with no-break spaces), never out of its cell
        assertThat(section).doesNotContain(".cl-table-pills .cl-status { white-space: nowrap; }")
                .contains(".cl-page .cl-table.is-warehouse td.is-numeric .cl-table-pills .cl-status { text-align: right; white-space: normal; }");
        assertThat(section).contains("-webkit-line-clamp: 2;").doesNotContain(".cl-table-code { white-space: normal; }");
    }

    @Test
    void phoneCardIsACompactGridWithoutColumnLabels() throws Exception {
        // given
        String section = section();

        // when / then
        // like the orders card A: the cells give their parts to one grid (display: contents) and drop their labels;
        // name and codes, then "3 szt. × cost" with the status pill, then category and delivery in one muted line
        String phone = section.substring(section.indexOf("/* phone card"));
        assertThat(phone).contains(".cl-page .cl-table.is-warehouse tbody tr { display: grid;")
                .contains("tbody :is(th.cl-table-key, td:not(.cl-table-check)) { display: contents; }")
                .contains("tbody :is(th, td)::before { content: none; }")
                .contains(".cl-page .cl-table.is-warehouse .cl-cards-hide { display: none; }");
        assertThat(section).contains(".cl-page .cl-table.is-warehouse .cl-cards-only { display: none; }");
    }

    @Test
    void theDeliveryLinkStaysOnOneLineInTheTableAndIsCutAtTheCellEdge() throws Exception {
        // given
        String section = section();

        // when / then
        // "8c12485 / 0" broke after the id at 1024 px; a longer link text ends in an ellipsis, whole in its title
        String table = section.substring(section.indexOf("@media screen and (min-width: 720px) {"));
        // 12 %: an 8-character delivery id (66 px) fits the cell at 1024 px, where 11 % left 62 px and cut it
        assertThat(table).contains(".cl-page .cl-table.is-warehouse .cl-col-delivery { width: 12%; }");
        assertThat(table).contains(".cl-page .cl-table.is-warehouse .cl-cell-delivery { display: inline-block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; vertical-align: top; }");
    }
}
