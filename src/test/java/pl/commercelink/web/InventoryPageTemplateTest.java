package pl.commercelink.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryPageTemplateTest {

    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/" + name), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    private static String assortment() throws Exception {
        return read("inventory.html");
    }

    private static String prices() throws Exception {
        return read("inventory-prices.html");
    }

    @Test
    void searchFormWorksWithoutJavaScriptAsAPlainGet() throws Exception {
        // when / then
        assertThat(prices()).contains("th:action=\"@{/dashboard/inventory/prices}\"").contains("method=\"get\"")
                .contains("name=\"q\"").doesNotContain("th:disabled");
    }

    @Test
    void priceComparisonExposesTheHooksTheScriptSwapsFragmentsInto() throws Exception {
        // when / then
        assertThat(prices()).contains("data-inventory-page").contains("id=\"inventory-results\"")
                .contains("aria-live=\"polite\"").contains("data-inventory-search-error")
                .contains("th:data-page-url=\"@{/dashboard/inventory/prices}\"")
                .contains("panel('inventory-prices'").contains("#{inventory.prices.title}");
    }

    @Test
    void priceComparisonHasNoTilesNoSourcesBarAndStaysNarrow() throws Exception {
        // when / then
        assertThat(prices()).doesNotContain("data-inventory-summary").doesNotContain("cl-inv-sources")
                .doesNotContain("is-wide").doesNotContain("inventory-browse :: browse");
    }

    @Test
    void supplierAssortmentCarriesTheTilesAndTheBrowseListWithoutTheCodeSearch() throws Exception {
        // when / then
        assertThat(assortment()).contains("data-inventory-summary").contains("inventory-browse :: browse")
                .contains("is-wide").contains("#{inventory.title}")
                .doesNotContain("data-inventory-search").doesNotContain("id=\"inventory-results\"");
    }

    @Test
    void neitherPageHasTheModeSwitchAnyMore() throws Exception {
        // when / then
        assertThat(assortment() + prices()).doesNotContain("cl-inv-modes").doesNotContain("inventory.view.")
                .doesNotContain("view=browse");
    }

    @Test
    void scriptLoadsTheSummaryOnlyWhereItsSlotExists() throws Exception {
        // given
        String script = Files.readString(Path.of("src/main/resources/static/js/inventory.js"), StandardCharsets.UTF_8);

        // when / then
        assertThat(script).contains("if (summarySlot)");
    }
}
