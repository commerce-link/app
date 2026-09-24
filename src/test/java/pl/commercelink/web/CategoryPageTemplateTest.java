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
        context.setVariable("filterQuery", "?status=all");
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
        assertThat(html).contains("MSI RTX 5070").contains("Outside the list").contains("Pending")
                .contains("value=\"p1\"").contains("action=\"/dashboard/catalogs/c1/category/k1/products/bulk\"");
    }

    /**
     * The checkbox itself is 18 px; the label around it is the target a finger taps (44 px below 1024 px, D-I5), for
     * every row and for "select visible" alike.
     */
    @Test
    void everyCheckboxOfTheTableSitsInALabelThatIsItsTouchTarget() {
        // when
        String html = rendered(false);

        // then
        assertThat(occurrences(html, "<label class=\"cl-check-target\">")).isEqualTo(3);
        assertThat(html).containsPattern("<label class=\"cl-check-target\">\\s*<input type=\"checkbox\" class=\"cl-check-input\" data-cl-select-all")
                .containsPattern("<label class=\"cl-check-target\">\\s*<input type=\"checkbox\" class=\"cl-check-input\" data-cl-select-row");
    }

    /** The marketplaces column is the one a table between 720 and 1023 px drops; an EAN is a code that never breaks. */
    @Test
    void theMarketplacesColumnIsSecondaryAndTheEanIsACode() {
        // when
        String html = rendered(false);

        // then
        assertThat(occurrences(html, "class=\"is-secondary-column\"")).isEqualTo(3);
        assertThat(html).containsPattern("<th scope=\"col\" class=\"is-secondary-column\">Marketplaces</th>")
                .containsPattern("<span class=\"cl-table-code\">EAN \\d+</span>");
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
        assertThat(html).contains("EAN 1").contains(" · <span class=\"cl-table-code\">MFN-1</span>").contains("· PIM pim-1");
    }

    /** A manufacturer code breaks at its hyphens ("MFN-" / "CLEAR-01") unless it is a code; the separator may wrap. */
    @Test
    void theManufacturerCodeIsACodeAndItsSeparatorStaysOutsideIt() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).containsPattern("<span> · <span class=\"cl-table-code\">MFN-1</span></span>");
    }

    @Test
    void theFiltersCountTheRowsTheyWouldShow() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("<span class=\"cl-segment-count\">1</span>")
                .contains("Listed on marketplaces (1)").contains("With a maximum price (0)")
                .contains("RTX 5070 (1)").contains("RTX 5060 (1)").contains("Subcategory: all (2)");
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

    /**
     * The bulk form renders no filter of its own: the script copies the address, which is the filter as the operator
     * left it, and a status rendered by the server would be the one the page was opened with.
     */
    @Test
    void theBulkFormCarriesNoFilterOfItsOwn() {
        // when
        String html = rendered(false);

        // then
        String form = html.substring(html.indexOf("<form"), html.indexOf("</form>"));
        assertThat(form).doesNotContain("name=\"status\"");
    }

    /** A row opens its product with the filter of the page, so the save comes back to it (QA 15.10). */
    @Test
    void aRowLinkCarriesTheFilterOfThePage() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("href=\"/dashboard/catalogs/c1/category/k1/products/p1?status=all\" data-cl-filter-carry")
                .contains("href=\"/dashboard/catalogs/c1/category/k1/products/new?status=all\" data-cl-filter-carry");
    }

    /**
     * A segment is read as "Active: 1", not "Active1". The separator is inline text of no size: a visually hidden span
     * is taken out of the flow, and Chromium then pads it with spaces in the name ("Active : 1").
     */
    @Test
    void aSegmentNamesItsCountApartFromItsLabel() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("<span>Active</span><span class=\"cl-segment-sep\">: </span><span class=\"cl-segment-count\">1</span>")
                .contains("<span>All</span><span class=\"cl-segment-sep\">: </span><span class=\"cl-segment-count\">2</span>");
    }

    /** The column is the subcategory of the category's list; "product subcategory" is the product page's field. */
    @Test
    void theSubcategoryColumnIsCalledSubcategory() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("data-sort-key=\"label\">Subcategory</button>").contains("data-label=\"Subcategory\"")
                .doesNotContain("Product subcategory");
    }

    /** An automatic category is defined by its recommendation filters: the lead says how many it has. */
    @Test
    void theLeadOfAnAutomaticCategoryCountsItsRecommendationFilters() {
        // when / then
        assertThat(rendered(true)).contains("Recommendation filters: 0");
        assertThat(rendered(false)).doesNotContain("Recommendation filters");
    }

    /** The currency is a message, not a literal of the template. */
    @Test
    void thePriceCurrencyComesFromTheMessages() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("PLN");
        assertThat(rendered(true)).contains("2 749,00 PLN");
    }

    /** The placeholder fits a narrow field; the full description is the field's name. */
    @Test
    void theSearchHasAShortPlaceholderAndAFullNameAndLivesInTheAddress() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).contains("data-cl-table-search=\"q\"").contains("placeholder=\"Search products\"")
                .contains("aria-label=\"Search products: name, EAN, code, PIM, brand\"");
    }

    /**
     * The count is announced from a region that is always in the page; a live region that appears in the same frame as
     * its text is often skipped. The bar keeps the visible count.
     */
    @Test
    void theSelectionIsAnnouncedFromAPermanentRegionOutsideTheBar() {
        // when
        String html = rendered(false);

        // then
        assertThat(html).containsPattern("<p class=\"cl-visually-hidden\" role=\"status\" data-cl-selection-status></p>\\s*<div class=\"cl-selection-bar\"");
        String bar = html.substring(html.indexOf("data-cl-selection-bar"), html.indexOf("cl-selection-actions"));
        assertThat(bar).doesNotContain("role=\"status\"");
    }
}
