package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.web.catalog.CategoryPageModel;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.catalog.ProductRow;
import pl.commercelink.web.catalog.ProductStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryPageTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/category.html"), StandardCharsets.UTF_8);
    }

    @Test
    void tableCarriesTheFilterGroupsAndSelectionHooks() throws Exception {
        // when / then
        assertThat(page()).contains("data-cl-table-filter").contains("data-cl-filter-default=${filterDefault}")
                .contains("data-cl-filter-multi=\"feature\"")
                .contains("data-cl-filter-group=\"status\"").contains("data-cl-filter-group=\"label\"")
                .contains("data-cl-filter-group=\"feature\"")
                .contains("data-cl-filter-status=").contains("data-cl-filter-label=").contains("data-cl-filter-feature=")
                .contains("data-cl-search=").contains("data-cl-table-search")
                .contains("data-cl-selection-bar").contains("data-cl-select-action=\"delete\"").contains("data-cl-select-form")
                .contains("fragments/confirm-dialog :: dialog")
                .contains("@{/js/table-filter.js}").contains("@{/js/table-sort.js}").contains("@{/js/table-select.js}")
                .contains("@{/js/confirm-dialog.js}");
    }

    /** Every option of a filter select states its own label: the script writes the count into the text only when it can. */
    @Test
    void everyFilterOptionStatesItsCountAndItsLabel() {
        // given
        String html = rendered(false);

        // when
        List<String> options = tagsOf(html, "option");

        // then
        assertThat(options).hasSize(9)
                .allSatisfy(option -> assertThat(option).contains("data-count=").contains("data-label="));
    }

    @Test
    void dynamicCategoryHasNoCheckboxesAddButtonOrBulkForm() throws Exception {
        // when / then
        assertThat(page()).contains("th:unless=\"${page.dynamic()}\" class=\"cl-button is-primary\"")
                .contains("th:unless=\"${page.dynamic()}\" class=\"cl-table-check\"")
                .contains("th:unless=\"${page.dynamic()}\" data-cl-select-form")
                .contains("#{catalog.products.note.dynamic}");
    }

    @Test
    void noOldWidgets() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("confirmBulkAction").doesNotContain("alert(").doesNotContain("overflow-x")
                .doesNotContain("style=").doesNotContain("bulkAction\" name=\"action\"")
                .doesNotContain("fragments/pagination").doesNotContain("class=\"button is-")
                .doesNotContain("notification is-").doesNotContain("dropdown").doesNotContain("errorMessage");
    }

    /** The page as the controller renders it: two manual products, one of them with a label outside the list. */
    private static String rendered(boolean dynamic) {
        ProductRow listed = new ProductRow("p1", "MSI RTX 5070", "1", "MFN-1", "pim-1", "MSI", "RTX 5070", false,
                "Default", List.of("Allegro"), ProductStatus.ACTIVE, Set.of("marketplace", "stock"),
                "msi rtx 5070", dynamic ? null : "/dashboard/catalogs/c1/category/k1/products/p1",
                dynamic ? "2 749,00" : null);
        ProductRow outside = new ProductRow("p2", "ASUS RTX 5060", "2", null, null, "ASUS", "RTX 5060", true,
                "Default", List.of(), ProductStatus.NO_PIM, Set.of(), "asus rtx 5060",
                dynamic ? null : "/dashboard/catalogs/c1/category/k1/products/p2", dynamic ? "1 999,00" : null);
        return rendered(dynamic, List.of(listed, outside));
    }

    /** The manual page with rows of the test's own making. */
    private static String renderedWith(List<ProductRow> rows) {
        return rendered(false, rows);
    }

    private static String rendered(boolean dynamic, List<ProductRow> rows) {
        ProductCatalog catalog = new ProductCatalog("store-1", "Parts");
        catalog.setCatalogId("c1");
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        gpu.setCategoryId("k1");
        gpu.setGroupingOrder(List.of("RTX 5070"));
        if (dynamic) {
            gpu.setType(CategoryDefinitionType.Dynamic);
            gpu.setPimCategoryIds(List.of("pim-gpu"));
        }
        CategoryPageModel page = CategoryPageModel.of(rows, gpu);
        Context context = new Context();
        context.setVariable("catalog", catalog);
        context.setVariable("category", gpu);
        context.setVariable("page", page);
        context.setVariable("statuses", ProductStatus.values());
        context.setVariable("features", CategoryPageModel.FEATURES);
        context.setVariable("filterDefault", "status:active feature:all label:all");
        context.setVariable("statusFilter", "active");
        context.setVariable("typeLabelKey", CategoryTypeLabels.labelKey(gpu.getType()));
        context.setVariable("typeTone", CategoryTypeLabels.tone(gpu.getType()));
        context.setVariable("pimNames", "Graphics cards");
        context.setVariable("categoryMarketplaceNames", List.of("Allegro"));
        context.setVariable("settingsHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("basicsHref", "/dashboard/catalogs/c1/category/k1/settings/basics");
        context.setVariable("addHref", "/dashboard/catalogs/c1/category/k1/products/add");
        context.setVariable("newProductHref", "/dashboard/catalogs/c1/category/k1/products/new");
        context.setVariable("bulkAction", "/dashboard/catalogs/c1/category/k1/products/bulk");
        context.setVariable("backHref", "/dashboard/catalogs/c1");
        context.setVariable("settingsSavedMessage", null);
        context.setVariable("categoryNotice", null);
        context.setVariable("catalogError", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category", context);
    }

    private static List<String> tagsOf(String html, String name) {
        Matcher matcher = Pattern.compile("<" + name + "\\b[^>]*>").matcher(html);
        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void theManualPageRendersOneHeaderTheRowsAndTheBulkFormOnce() {
        // when
        String html = rendered(false);

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(occurrences(html, "data-cl-select-form")).isEqualTo(1);
        assertThat(occurrences(html, "data-cl-select-row")).isEqualTo(2);
        assertThat(html).contains("data-cl-select-table");
        assertThat(html.indexOf("cl-page-actions")).isLessThan(html.indexOf("/products/add"));
        assertThat(html).contains("Manual").doesNotContain("??");
        assertThat(html).contains("MSI RTX 5070").contains("Outside the list").contains("No PIM")
                .contains("value=\"p1\"").contains("action=\"/dashboard/catalogs/c1/category/k1/products/bulk\"")
                .contains("name=\"status\" value=\"active\"");
    }

    /** A product known only by its manufacturer code: the line under its name states what it has, not "EAN null". */
    @Test
    void aProductWithoutAnEanShowsItsManufacturerCodeAlone() {
        // given
        ProductRow mfnOnly = new ProductRow("p3", "Gigabyte RTX 5050", null, "MFN-3", null, "Gigabyte", "RTX 5070",
                false, "Default", List.of(), ProductStatus.ACTIVE, Set.of(), "gigabyte rtx 5050",
                "/dashboard/catalogs/c1/category/k1/products/p3", null);

        // when
        String html = renderedWith(List.of(mfnOnly));

        // then
        assertThat(html).doesNotContain("EAN null").doesNotContain("· MFN-3").contains(">MFN-3<");
    }

    /** The same product with an EAN: the codes stay on one line, separated. */
    @Test
    void aProductWithAnEanAndAManufacturerCodeShowsBothSeparated() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("EAN 1").contains("· MFN-1").contains("· PIM pim-1");
    }

    @Test
    void theFiltersCountTheRowsTheyWouldShow() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("<span class=\"cl-segment-count\">1</span>")
                .contains("Listed on marketplaces (1)").contains("With a maximum price (0)")
                .contains("RTX 5070 (1)").contains("RTX 5060 (1)").contains("Label: all (2)");
    }

    /**
     * An automatic category offers no "Pokaż" select: the export publishes a category's own products and an automatic
     * category has none, so there is no marketplace subset to show. Without the select the filter script never puts the
     * "feature" group into its state, and the legacy {@code feature=marketplace} address simply opens the whole list.
     */
    @Test
    void theAutomaticPageOffersNoFeatureSelect() {
        // when
        String html = rendered(true);

        // then
        assertThat(html).doesNotContain("feature-filter").doesNotContain("data-cl-filter-group=\"feature\"")
                .contains("MSI RTX 5070").contains("ASUS RTX 5060");
    }

    @Test
    void theAutomaticPageIsPricedAndOffersNothingToSelectOrAdd() {
        // when
        String html = rendered(true);

        // then
        assertThat(html).doesNotContain("data-cl-select-table").doesNotContain("data-cl-select-row").doesNotContain("data-cl-select-form")
                .doesNotContain("data-cl-selection-bar").doesNotContain("/products/add");
        assertThat(html).contains("data-sort-price=\"2 749,00\"").contains("2 749,00 PLN")
                .contains("Lowest gross price").doesNotContain("??");
    }
}
