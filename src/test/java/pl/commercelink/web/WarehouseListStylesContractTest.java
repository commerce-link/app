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
        assertThat(section).contains(".cl-page .cl-table.is-warehouse .cl-col-status { width: 9.5rem; }")
                .doesNotContain(".cl-col-status { width: 16%; }");
    }

    @Test
    void categoryCellHyphenatesASingleLongWordInsteadOfCuttingIt() throws Exception {
        // given
        String section = section();

        // when / then
        assertThat(section).contains(".cl-page .cl-table.is-warehouse .is-hyphenated { hyphens: auto; }");
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
}
