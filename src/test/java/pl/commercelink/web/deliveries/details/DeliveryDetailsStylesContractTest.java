package pl.commercelink.web.deliveries.details;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryDetailsStylesContractTest {

    private static String section() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/static/css/commercelink.css"), StandardCharsets.UTF_8);
        int start = css.indexOf("/* --- Delivery details");
        assertThat(start).as("the delivery details block").isPositive();
        return css.substring(start);
    }

    @Test
    void theBlockDefinesEveryNewComponentOfTheSpec() throws Exception {
        // when
        String section = section();

        // then
        assertThat(section).contains(".cl-table.is-allocations col.cl-col-received")
                .contains(".cl-table.is-allocations tr.cl-alloc-row").contains(".cl-received.is-complete")
                .contains(".cl-selection-row.is-docked.is-in-view:not([hidden])").contains("--cl-docked-bar")
                .contains(".cl-card.is-status.is-bad").contains(".cl-status-actions").contains(".cl-dialog-list")
                .contains(".cl-dialog-actions-end").contains(".cl-meta-truncate").contains(".cl-kv-text.is-break")
                .contains(".cl-layout-side.is-grid").contains("repeat(auto-fill, minmax(320px, 1fr))")
                .contains(".cl-choice-reveal").contains("dialog.cl-dialog[open]:not(:modal)");
    }

    @Test
    void theBlockStaysWithinTheBreakpointsAndNeverReordersTheDom() throws Exception {
        // when
        String section = section();

        // then
        assertThat(section.replaceAll("\\(max-width: (719|1023|1215|1365)px\\)|\\(min-width: (720|1024|1216|1366)px\\)", ""))
                .doesNotContainPattern("\\((max|min)-width:");
        assertThat(section).doesNotContainPattern("[\\s;{]order:").doesNotContain("--cl-ok:").doesNotContain("!important");
    }

    @Test
    void theSideGridOnlyAppliesBetweenThePhoneAndTheTwoColumnLayout() throws Exception {
        // when
        String section = section();

        // then
        assertThat(section).contains("@media screen and (min-width: 720px) and (max-width: 1365px) {\n    .cl-page .cl-layout-side.is-grid {");
    }

    @Test
    void statusCardRulesNeverReachTheExistingFlatStatusCards() throws Exception {
        // when
        String section = section();

        // then
        for (String line : section.split("\\n")) {
            if (line.contains(".cl-card.is-status") && line.trim().endsWith("{")) {
                assertThat(line).as("selector must be scoped to the tone modifiers or the new status parts")
                        .containsPattern("\\.cl-card\\.is-status(\\.is-(bad|warn|info)|:is\\(\\.is-bad, \\.is-warn, \\.is-info\\)| \\.cl-status-(reason|actions)| \\.cl-card-title \\.is-(bad|info))");
            }
        }
    }

    @Test
    void aLongSupplierNumberIgnoresTheTemplateWhitespaceThatPreLineWouldTurnIntoBlankLines() throws Exception {
        // when
        String section = section();
        // as specific as the pre-line rule (.cl-page .cl-kv.is-column dd.cl-kv-text) and one class more, so it wins
        int rule = section.indexOf(".cl-page .cl-kv.is-column dd.cl-kv-text.is-break {");

        // then
        assertThat(rule).isPositive();
        assertThat(section.substring(rule, section.indexOf('}', rule))).contains("white-space: normal;");
    }

    @Test
    void aLongUnbreakableDestinationWrapsInsteadOfWideningThePhoneLayout() throws Exception {
        // when
        String section = section();
        int rule = section.indexOf(".cl-page .cl-table.is-allocations tr.cl-alloc-row .cl-alloc-dest > * {");

        // then
        assertThat(rule).isPositive();
        assertThat(section.substring(rule, section.indexOf('}', rule)))
                .contains("max-width: 100%;").contains("overflow-wrap: anywhere;");
    }

    @Test
    void theDockedBarIsFixedBelowTheScrimAndEveryMediaQueryIsScreenOnly() throws Exception {
        // when
        String section = section();
        int docked = section.indexOf("position: fixed");

        // then
        assertThat(docked).isPositive();
        assertThat(section.substring(docked, docked + 200)).contains("z-index: 15");
        assertThat(section).doesNotContainPattern("@media \\((?!prefers-reduced-motion)");
    }
}
