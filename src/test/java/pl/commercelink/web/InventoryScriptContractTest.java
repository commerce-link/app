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
    void spinnerAndSkeletonAreSharedAndKeepAReducedMotionFallback() throws Exception {
        // given
        String shared = read("src/main/resources/static/css/commercelink.css");

        // when / then
        assertThat(shared).contains(".cl-spinner {").contains("@keyframes cl-spin").contains("@keyframes cl-spin-pulse")
                .contains(".cl-skeleton {").contains("@keyframes cl-shimmer");
    }

    @Test
    void offersTableHasFixedColumnsAStickyHeaderAndDirectionalSortMarkers() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");
        String shared = read("src/main/resources/static/css/commercelink.css");

        // when / then -- the column widths and the sticky header belong to this page, the sort marker to .cl-table
        assertThat(css).contains(".cl-inv-col-price {").contains("position: sticky").contains("top: var(--cl-topbar-height)")
                .contains("tr.cl-inv-offer:hover td");
        assertThat(shared).contains("th[aria-sort=\"ascending\"] .cl-table-sort::after");
    }

    /**
     * The page is built from the shared components; a copy of one under a `cl-inv-` name is how the two
     * drifted apart before (the page button grew to 40px while `.cl-button` stayed 36px).
     */
    @Test
    void pageDoesNotKeepItsOwnCopyOfASharedComponent() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css)
                .doesNotContain(".cl-inv-button").doesNotContain(".cl-inv-input").doesNotContain(".cl-inv-card {")
                .doesNotContain(".cl-inv-title").doesNotContain(".cl-inv-lead").doesNotContain(".cl-inv-eyebrow")
                .doesNotContain(".cl-inv-visually-hidden").doesNotContain(".cl-inv-field-error")
                .doesNotContain(".cl-inv-table {").doesNotContain(".cl-inv-sort").doesNotContain(".cl-inv-skeleton {")
                .doesNotContain("\n.cl-spinner {");
    }

    /** Only the frame's breakpoints: a page-specific one is how the offers table ended up folding at 900px. */
    @Test
    void styleSheetUsesOnlyTheBreakpointsOfTheFrame() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains("@media screen and (max-width: 719px)")
                .doesNotContain("899px").doesNotContain("900px");
    }

    /**
     * A marker that only appears on hover cannot be discovered on a touch screen, where the offers table
     * is exactly the place someone wants to reorder by price.
     */
    @Test
    void sortableHeadersShowTheirMarkerWithoutHovering() throws Exception {
        // given
        String shared = read("src/main/resources/static/css/commercelink.css");
        String sortMarker = shared.substring(shared.indexOf(".cl-page .cl-table-sort::after"));
        sortMarker = sortMarker.substring(0, sortMarker.indexOf("}"));

        // when / then
        assertThat(sortMarker).contains("opacity: .3").doesNotContain("opacity: 0;");
    }

    /**
     * The panel used to collapse by animating a fixed max-height, which cut the dismiss button off on a
     * phone whenever the copy wrapped past it.
     */
    @Test
    void screenIntroCollapsesWithoutAHeightCeilingAndStaysHideable() throws Exception {
        // given
        String fragment = read("src/main/resources/templates/fragments/screen-intro.html");

        // when / then
        assertThat(fragment).doesNotContain("max-height: 640px")
                .contains("grid-template-rows: 1fr").contains("grid-template-rows: 0fr")
                // `display: grid` outranks the user-agent rule for the attribute
                .contains(".screen-intro[hidden]");
    }

    /** Six columns only stay readable on a phone when each figure says what it is. */
    @Test
    void offersBecomeLabelledCardsWithAnExplicitSortBarOnAPhone() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");
        String template = read("src/main/resources/templates/fragments/inventory-results.html");

        // when / then
        assertThat(css).contains("td[data-label]::before").contains("content: attr(data-label)")
                .contains("content: attr(data-sort-label)");
        assertThat(template).contains("data-sort-label=#{inventory.table.sortBy}")
                .contains("data-label=#{inventory.table.delivery}").contains("data-label=#{inventory.table.total}");
    }

    @Test
    void offersTableReservesAColumnForEveryFigureItCompares() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains(".cl-inv-col-delivery {").contains(".cl-inv-col-total {")
                .contains(".cl-inv-col-lead {").contains(".cl-inv-col-pick {");
    }

    /** Searching again, or pressing back, must not silently drop the order the page was opened for. */
    @Test
    void selectionModeSurvivesEveryNavigationOfThePage() throws Exception {
        // given
        String script = read("src/main/resources/static/js/inventory.js");
        String page = read("src/main/resources/templates/inventory.html");

        // when / then
        assertThat(page).contains("th:data-selection=").contains("type=\"hidden\" name=\"for\"");
        assertThat(script).contains("page.dataset.selection").contains("'&for=' + encodeURIComponent(selection)")
                .contains("selectionParam()");
    }

    @Test
    void styleSheetForcesHiddenElementsToStayHidden() throws Exception {
        // given
        String css = read("src/main/resources/static/css/inventory.css");

        // when / then
        assertThat(css).contains(".cl-inv-page [hidden]");
    }
}
