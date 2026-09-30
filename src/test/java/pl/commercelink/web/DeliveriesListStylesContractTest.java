package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
                .contains(".cl-table-toolbar.is-collapsed");
        assertThat(section.replaceAll("\\(max-width: (719|1023|1215)px\\)|\\(min-width: (720|1024|1216)px\\)", ""))
                .doesNotContainPattern("\\((max|min)-width:");
        assertThat(section).doesNotContain("--cl-ok:").doesNotContain(".cl-status {");
    }
}
