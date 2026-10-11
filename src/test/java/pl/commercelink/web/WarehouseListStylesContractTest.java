package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Layout rules of the warehouse list table that the browser checks (E2E, Task 11) found missing. */
class WarehouseListStylesContractTest {

    private static String css() throws Exception {
        return Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
    }

    private static String section() throws Exception {
        String css = css();
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
    void dockedSelectionRowIsOneSharedBlockBelowTheDrawerScrim() throws Exception {
        // given
        String css = css();
        String rule = ".cl-page .cl-selection-row.is-docked.is-in-view:not([hidden]) {";

        // when / then
        // the warehouse list uses the docked row of the delivery details (PR #259) as is: one block for both pages;
        // z-index 15 sits below the drawer's scrim (--cl-z-scrim 20), the room under the list is the measured --cl-docked-bar
        assertThat(css.split(java.util.regex.Pattern.quote(rule), -1)).hasSize(2);
        assertThat(css).contains("""
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
        assertThat(section()).doesNotContain("z-index: 30").doesNotContain(":has(.cl-selection-row:not([hidden]))")
                .doesNotContain("rgba(15, 23, 42");
    }

    @Test
    void menusOfTheDockedRowOpenUpwardsAndScrollWithinTheRoomBelowTheTopBar() throws Exception {
        // given
        String css = css();

        // when / then
        // pinned to the bottom edge the row has nothing below it; menu.js turns a list up only when it fits above whole,
        // so in a short window (1280x720 at 200 %) the "Zmień status" list stayed off the screen
        assertThat(css).contains("""
                    .cl-page .cl-selection-row.is-docked.is-in-view:not([hidden]) .cl-menu-list {
                        top: auto;
                        bottom: calc(100% + 4px);
                        max-height: calc(100vh - var(--cl-docked-bar, 112px) - var(--cl-topbar-height) - 8px);
                        max-height: calc(100dvh - var(--cl-docked-bar, 112px) - var(--cl-topbar-height) - 8px);
                        overflow-y: auto;
                """);
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
        assertThat(section).contains("-webkit-line-clamp: 2;");
        assertThat(table.substring(0, table.indexOf("@media screen and (max-width: 719px)"))).doesNotContain(".cl-table-code { white-space: normal");
    }

    @Test
    void aCodeLongerThanThePhoneCardWrapsInsteadOfWideningThePage() throws Exception {
        // given
        String section = section();

        // when / then
        // the shared rule keeps a code on one line (an EAN is read digit by digit); a 46-character code pushed a 390 px
        // page to 482 px, and the ellipsis of the table has no tooltip on touch, so on the card it may break anywhere
        String phone = section.substring(section.indexOf("@media screen and (max-width: 719px)"));
        assertThat(phone).contains(".cl-page .cl-table.is-warehouse .cl-table-code { white-space: normal; overflow-wrap: anywhere; }");
    }

    @Test
    void categoryColumnIsWiderWhereTheSidebarNarrowsTheTableBelow1280() throws Exception {
        // given
        String section = section();

        // when / then
        // at 1024 px the table is 786 px and 12 % left 70 px of text, three short of "Chłodzenie" (73 px at 14 px)
        String block = section.substring(section.indexOf("@media screen and (min-width: 1024px) and (max-width: 1279px)"));
        assertThat(block.substring(0, block.indexOf("}\n}") + 3)).contains(".cl-page .cl-table.is-warehouse .cl-col-category { width: 14%; }");
    }

    @Test
    void categoryColumnTakesTheRoomOfTheHiddenDeliveryColumnBelow1024() throws Exception {
        // given
        String section = section();

        // when / then
        // 12 % left 78 px at 768 px and broke "Chłodzenie" inside the word; the delivery column is hidden there anyway
        String narrow = section.substring(section.indexOf("@media screen and (min-width: 720px) and (max-width: 1023px)"));
        assertThat(narrow.substring(0, narrow.indexOf("}\n}") + 3)).contains(".cl-page .cl-table.is-warehouse .cl-col-category { width: 18%; }");
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
