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
}
