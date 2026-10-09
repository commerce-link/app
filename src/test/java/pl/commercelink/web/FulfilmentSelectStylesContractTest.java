package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the supplier selection styles: the solid supplier palette, offers as separated card rows with their states, the
 * toggle label, the phone card mode that never reorders the DOM, and the summary peek that only exists while the
 * summary sits under the table.
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
    void thePriceSliderTakesThePointerOnlyOnItsThumbsAndGrowsThemForTouch() throws Exception {
        // given
        String css = section();

        // when
        String input = ruleBody(css, ".cl-page .cl-range input[type=\"range\"]");
        String thumb = ruleBody(css, ".cl-page .cl-range input[type=\"range\"]::-webkit-slider-thumb");
        int touch = css.indexOf("@media screen and (max-width: 1023px) {\n    .cl-page .cl-range input[type=\"range\"]::-webkit-slider-thumb {");

        // then
        assertThat(input).contains("pointer-events: none;").contains("height: 44px;");
        assertThat(thumb).contains("pointer-events: auto;").contains("var(--cl-accent)");
        assertThat(ruleBody(css, ".cl-page .cl-filter-menu-dates.is-pair")).contains("repeat(2, minmax(0, 1fr))");
        assertThat(touch).as("the touch thumb block").isNotNegative();
        assertThat(ruleBody(css.substring(touch), ".cl-page .cl-range input[type=\"range\"]::-webkit-slider-thumb")).contains("width: 28px;");
    }

    @Test
    void thePaletteIsSolidTokensWithWhiteText() throws Exception {
        // given
        String css = section();

        // when / then
        for (int i = 0; i <= 6; i++) {
            assertThat(css).contains("--cl-supplier-" + i + ":")
                    .contains(".cl-page .cl-supplier-pill.is-c" + i + " {");
            assertThat(ruleBody(css, ".cl-page .cl-supplier-pill.is-c" + i)).contains("background: var(--cl-supplier-" + i + ");");
        }
        assertThat(css).contains("--cl-supplier-1: #1b4db1;").contains("--cl-supplier-2: #9a3f0f;").contains("--cl-supplier-3: #1d6b45;")
                .contains("--cl-supplier-4: #6a2fa0;").contains("--cl-supplier-5: #0b6170;").contains("--cl-supplier-6: #8f1659;")
                .contains("--cl-supplier-0: #47566a;").doesNotContain("-soft:").doesNotContain("cl-supplier-dot");
        assertThat(ruleBody(css, ".cl-page .cl-supplier-pill")).contains("color: var(--cl-on-accent);");
    }

    @Test
    void offersAreSeparatedCardRowsWithATintedSelectedState() throws Exception {
        // given
        String css = section();

        // when / then
        assertThat(ruleBody(css, ".cl-page .cl-table.is-supplier-offers")).contains("border-collapse: separate;").contains("border-spacing: 0 8px;");
        assertThat(ruleBody(css, ".cl-page .cl-table.is-supplier-offers tbody tr.is-on > *")).contains("var(--cl-offer-on)");
        assertThat(ruleBody(css, ".cl-page .cl-table.is-supplier-offers tbody tr.is-on > :first-child")).contains("inset 5px 0 0 var(--cl-accent)");
        assertThat(ruleBody(css, ".cl-page .cl-table.is-supplier-offers tbody tr.is-covered > *")).contains("var(--cl-surface-2)");
        assertThat(css).contains(".cl-page .cl-table.is-supplier-offers tr.is-off");
        // the states match the white card row's specificity, so they must come after it to win
        int card = css.indexOf(".cl-page .cl-table.is-supplier-offers tbody tr:is([data-cl-offer], .is-missing) > * {");
        assertThat(card).isPositive();
        assertThat(css.indexOf(".cl-page .cl-table.is-supplier-offers tbody tr.is-on > * {")).isGreaterThan(card);
        assertThat(css.indexOf(".cl-page .cl-table.is-supplier-offers tbody tr.is-covered > * {")).isGreaterThan(card);
        assertThat(css.indexOf(".cl-page .cl-table.is-supplier-offers tbody tr.is-missing > * {")).isGreaterThan(card);
    }

    @Test
    void theToggleFollowsItsCheckboxAndShowsTheFocus() throws Exception {
        // given
        String css = section();

        // when / then
        assertThat(ruleBody(css, ".cl-page .cl-offer-toggle:has(input:checked)")).contains("background: var(--cl-accent);");
        assertThat(ruleBody(css, ".cl-page .cl-offer-toggle:has(input:focus-visible)")).contains("outline: 2px solid var(--cl-accent);");
        assertThat(ruleBody(css, ".cl-page .cl-offer-toggle input")).contains("opacity: 0;");
    }

    @Test
    void theCardModePlacesEveryCellExplicitlyWithTheToggleAcrossTheBottom() throws Exception {
        // given
        String css = section();
        int media = css.indexOf("@media screen and (max-width: 719px)");
        assertThat(media).isPositive();
        String block = css.substring(media);

        // when / then
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers th.cl-table-key")).contains("grid-column: 1 / -1;").contains("grid-row: 1;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-qty")).contains("grid-column: 1;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-price")).contains("grid-column: 2;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-profit")).contains("grid-column: 3;");
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers td.cl-cell-toggle")).contains("grid-column: 1 / -1;").contains("grid-row: 3;");
    }

    @Test
    void touchTargetsGrowBelowDesktop() throws Exception {
        // given
        String css = section();
        int media = css.indexOf("@media screen and (max-width: 1023px)");

        // when / then
        assertThat(media).isPositive();
        String block = css.substring(media, css.indexOf("\n}\n", media));
        assertThat(block).contains(".cl-page .cl-offer-toggle").contains("min-height: 44px;");
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
    void theCardModeDropsTheBaseColumnLabels() throws Exception {
        // given
        String css = section();
        String block = css.substring(css.indexOf("@media screen and (max-width: 719px)"));

        // when / then
        assertThat(ruleBody(block, ".cl-page .cl-table.is-supplier-offers tbody tr > *::before")).contains("content: none;");
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
        assertThat(css.replace(block, "")).doesNotContain("tr[data-cl-offer]:hover");
    }
}
