package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveriesListStylesContractTest {

    @Test
    void deliveriesSectionDefinesItsComponentsAndStaysWithinTheBreakpoints() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int start = css.indexOf("/* --- Deliveries list");
        int end = css.indexOf("/* ---", start + 10);
        String section = css.substring(start, end < 0 ? css.length() : end);

        // then
        assertThat(start).isPositive();
        assertThat(section).contains("a.cl-segment[aria-current=\"page\"]")
                .contains(".cl-filter-menu.is-end").contains(".cl-table.is-deliveries .cl-status")
                .contains(".cl-table.is-deliveries .is-secondary-column").contains(".cl-toolbar-toggle")
                .contains(".cl-table-toolbar.is-collapsed")
                .contains("grid-template-columns: repeat(2, minmax(132px, 1fr))");
        assertThat(section.replaceAll("\\(max-width: (719|1023|1215)px\\)|\\(min-width: (720|1024|1216)px\\)", ""))
                .doesNotContainPattern("\\((max|min)-width:");
        assertThat(section).doesNotContain("--cl-ok:");
        assertThat(section).doesNotContainPattern(Pattern.compile("(?m)^\\.cl-status \\{"));
    }

    @Test
    void phoneDatePanelSpansTheRowAndStatusColumnKeepsTheTableInsideTheCardAtTheRailWidth() throws Exception {
        // given
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        String section = css.substring(css.indexOf("/* --- Deliveries list"));
        String phone = section.substring(section.indexOf("@media screen and (max-width: 719px)"));

        // then: a right-aligned 302 px panel overflowed a 320 px screen on the left
        assertThat(phone).contains(".cl-page .cl-filter-menu.is-end .cl-filter-menu-panel {\n        left: 0;\n        right: 0;\n        min-width: 0;")
                .contains("grid-template-columns: minmax(0, 1fr)");
        // then: 15ch for the status column pushed the table 26 px past the card at 720 px, so it applies from 1024 px only
        assertThat(section).containsPattern(Pattern.compile("@media screen and \\(min-width: 720px\\) \\{\\s+\\.cl-page \\.cl-table\\.is-deliveries td\\[data-label\\]:has\\(\\.cl-status\\) \\{\\s+min-width: 11ch;"))
                .containsPattern(Pattern.compile("@media screen and \\(min-width: 1024px\\) \\{\\s+\\.cl-page \\.cl-table\\.is-deliveries td\\[data-label\\]:has\\(\\.cl-status\\) \\{\\s+min-width: 15ch;"));
    }
}
