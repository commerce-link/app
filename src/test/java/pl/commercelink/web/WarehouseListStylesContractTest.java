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
    void selectedUnitsAreSeparatedFromTheSelectedCountByADot() throws Exception {
        // given
        String section = section();

        // when / then
        // spec §4.5: "Zaznaczono {k} z {n} · {m} szt."
        assertThat(section).contains(".cl-page .cl-selection-row [data-cl-selection-units]::before { content: \"· \"; }");
    }

    @Test
    void dockedSelectionRowLeavesRoomForTheLastCardAndThePagesOnPhones() throws Exception {
        // given
        String section = section();

        // when / then
        // the fixed row at the bottom of a phone screen covered the last card and the pagination
        assertThat(section).contains(".cl-page .cl-card:has(.cl-table.is-warehouse):has(.cl-selection-row:not([hidden])) {");
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
