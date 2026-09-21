package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryScriptContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void scriptUsesOnlyHooksTheTemplatesRender() throws Exception {
        // given
        String script = read("src/main/resources/static/js/inventory.js");
        String templates = read("src/main/resources/templates/inventory.html")
                + read("src/main/resources/templates/fragments/inventory-summary.html")
                + read("src/main/resources/templates/fragments/inventory-results.html")
                + read("src/main/resources/templates/fragments/inventory-technical.html");

        // when / then
        for (String hook : List.of("data-inventory-page", "data-inventory-summary", "data-inventory-search",
                "data-inventory-clear", "data-inventory-submit", "data-inventory-field-error", "data-inventory-search-error",
                "data-inventory-search-retry", "data-inventory-live", "data-inventory-empty-template",
                "data-inventory-slot", "data-inventory-sources-toggle", "data-when-collapsed", "data-when-expanded",
                "data-inventory-spinner", "data-inventory-warehouse-products", "data-warehouse-products-label", "data-inventory-results-heading", "data-inventory-offers",
                "data-inventory-sortable", "data-sort-key", "data-announce", "data-inventory-fragment")) {
            assertThat(script).as("script references " + hook).contains(hook);
            assertThat(templates).as("templates render " + hook).contains(hook);
        }
    }

    /**
     * The status tones and the `.cl-status` pill are part of the shared design system; this page only adds
     * the tint behind the cheapest row. Redefining either here would fork the design system on one screen.
     */
    @Test
    void statusTonesComeFromTheSharedStyleSheetAndOnlyTheCheapestRowTintIsLocal() throws Exception {
        // given
        String shared = read("src/main/resources/static/css/commercelink.css");
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(shared)
                .contains("--cl-ok: #1d6b45").contains("--cl-warn: #8a4b00").contains("--cl-info: #1b4db1")
                .contains(".cl-status.is-ok").contains(".cl-status.is-info").contains(".cl-status.is-neutral");
        // page-scoped spacing for the pill inside a table cell stays here; the pill itself must not be redefined
        assertThat(css).contains("--cl-ok-tint")
                .doesNotContain("--cl-ok:").doesNotContain("--cl-warn:").doesNotContain("--cl-info:")
                .doesNotContain("\n.cl-status {").doesNotContain(".cl-status::before").doesNotContain(".cl-status.is-");
    }

    @Test
    void styleSheetUsesTheSharedTonesAndKeepsItsResponsiveRules() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains("var(--cl-info)").contains("var(--cl-ok-tint)")
                .contains("prefers-reduced-motion").contains("@media screen and (max-width: 719px)");
    }

    @Test
    void styleSheetDefinesTheSpinnerWithAReducedMotionFallback() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains(".cl-spinner {").contains("@keyframes cl-spin").contains("@keyframes cl-spin-pulse")
                .contains(".cl-inv-count {");
    }

    @Test
    void offersTableHasFixedColumnsAStickyHeaderAndDirectionalSortMarkers() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains(".cl-inv-col-price {").contains("position: sticky").contains("top: var(--cl-topbar-height)")
                .contains("th[aria-sort=\"ascending\"] .cl-inv-sort::after").contains("tr.cl-inv-offer:hover td");
    }

    @Test
    void styleSheetForcesHiddenElementsToStayHidden() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains(".cl-inv-page [hidden]");
    }
}
