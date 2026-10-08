package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the supplier selection styles: the supplier palette tokens, the three row states, the card mode that never
 * reorders the DOM, and the summary peek that only exists while the summary sits under the table.
 */
class FulfilmentSelectStylesContractTest {

    private static String section() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int start = css.indexOf("/* Supplier selection (POST /dashboard/orders/fulfilment");
        assertThat(start).as("the supplier selection block").isPositive();
        return css.substring(start);
    }

    private static String ruleBody(String css, String head) {
        int rule = css.indexOf(head + " {");
        assertThat(rule).as(head).isNotNegative();
        return css.substring(rule, css.indexOf('}', rule));
    }

    @Test
    void thePaletteIsTokensWithASoftTintForEveryColour() throws Exception {
        // given
        String css = section();

        // when / then
        for (int i = 1; i <= 6; i++) {
            assertThat(css).contains("--cl-supplier-" + i + ":").contains("--cl-supplier-" + i + "-soft:")
                    .contains(".cl-page .cl-supplier-pill.is-c" + i + " {");
        }
        assertThat(ruleBody(css, ".cl-page .cl-supplier-pill")).contains("color: var(--cl-ink);");
    }

    @Test
    void rowStatesAreDrawnFromTokens() throws Exception {
        // given
        String css = section();

        // when / then
        assertThat(ruleBody(css, ".cl-page .cl-table.is-supplier-offers tr.is-on > td.cl-table-check")).contains("var(--cl-accent)");
        assertThat(ruleBody(css, ".cl-page .cl-table.is-supplier-offers tr.is-covered > *")).contains("var(--cl-surface-2)");
        assertThat(css).contains(".cl-page .cl-table.is-supplier-offers tr.is-off");
    }

    @Test
    void theCardModePlacesEveryCellExplicitly() throws Exception {
        // given
        String css = section();
        int media = css.indexOf("@media screen and (max-width: 719px)");
        assertThat(media).isPositive();
        String block = css.substring(media);

        // when / then
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-table-check")).contains("grid-column: 1;").contains("grid-row: 1 / span 2;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-qty")).contains("grid-column: 2;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-price")).contains("grid-column: 3;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-profit")).contains("grid-column: 4;");
    }

    @Test
    void thePeekDisappearsWhenTheSummaryIsBesideTheTable() throws Exception {
        // given
        String css = section();

        // when / then
        assertThat(ruleBody(css, ".cl-page .cl-summary-peek")).contains("position: sticky;").contains("bottom: 0;");
        assertThat(css).contains("@media screen and (min-width: 1366px) {\n    .cl-page .cl-summary-peek {\n        display: none;");
    }

    @Test
    void theBlockKeepsTheSharedRules() throws Exception {
        // given
        String css = section();

        // when / then
        assertThat(css).doesNotContainPattern("[\\s;{]order:").doesNotContain("!important");
        assertThat(css.replaceAll("\\(max-width: (719|1023|1215|1365)px\\)|\\(min-width: (720|1024|1216|1366)px\\)", ""))
                .doesNotContainPattern("\\((max|min)-width:");
    }

    @Test
    void theCardLabelsSurviveTheResetOfTheBaseLabels() throws Exception {
        // given
        String css = section();
        String block = css.substring(css.indexOf("@media screen and (max-width: 719px)"));

        // when / then
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers tbody tr[data-cl-offer] > :is(td.cl-cell-qty, td.cl-cell-price, td.cl-cell-profit)::before"))
                .contains("content: attr(data-label);");
        assertThat(block).doesNotContain("tr[data-cl-offer] > ::before");
    }

    @Test
    void hoverOnlyPaintsRowsThatAreNeitherSelectedNorCoveredAndOnlyOnHoverDevices() throws Exception {
        // given
        String css = section();
        int media = css.indexOf("@media screen and (hover: hover) {");

        // when / then
        assertThat(media).isPositive();
        String block = css.substring(media, css.indexOf("\n}\n", media));
        assertThat(block).contains("tr[data-cl-offer]:not(.is-on):not(.is-covered):hover > *");
        assertThat(block).contains("tr.is-off:not(.is-on):not(.is-covered):hover td:not(.cl-table-check)").contains("color: var(--cl-ink-2);");
        assertThat(css.replace(block, "")).doesNotContain("tr[data-cl-offer]:hover");
    }
}
