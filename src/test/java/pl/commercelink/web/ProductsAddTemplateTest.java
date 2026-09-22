package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.web.catalog.RecommendationRow;
import pl.commercelink.web.dtos.ProductsBulkAddForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ProductsAddTemplateTest {

    private static String source(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/" + template + ".html"), StandardCharsets.UTF_8);
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    private static List<String> tagsOf(String html, String name) {
        Matcher matcher = Pattern.compile("<" + name + "\\b[^>]*>").matcher(html);
        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    @Test
    void theProposalsCarryTheFilterGroupAndTheSelectionHooks() throws Exception {
        // when / then
        assertThat(source("products-add")).contains("data-cl-table-filter").contains("data-cl-filter-default=\"brand:all\"")
                .contains("data-cl-filter-group=\"brand\"").contains("data-cl-filter-brand=")
                .contains("data-cl-search=").contains("data-cl-table-search")
                .contains("data-cl-selection-bar").contains("data-cl-selection-count").contains("data-cl-select-clear")
                .contains("data-cl-select-table=\"true\"").contains("data-cl-select-all").contains("data-cl-select-row")
                .contains("@{/js/table-filter.js}").contains("@{/js/table-sort.js}").contains("@{/js/table-select.js}");
    }

    /**
     * A brand may contain a space, so its group must never be declared multi-valued; the page confirms nothing by
     * itself, so it neither declares a bulk action nor loads the dialog.
     */
    @Test
    void theBrandGroupIsSingleValuedAndNothingIsConfirmed() throws Exception {
        // when / then
        assertThat(source("products-add")).doesNotContain("data-cl-filter-multi=").doesNotContain("data-cl-select-action")
                .doesNotContain("confirm-dialog").doesNotContain("data-cl-select-form");
    }

    @Test
    void neitherPageCarriesTheOldWidgetsOrInlineStyle() throws Exception {
        // given
        String add = source("products-add");
        String review = source("products-add-review");

        // when / then
        assertThat(add).doesNotContain("toggleAllRecommendations").doesNotContain("alert(").doesNotContain("style=")
                .doesNotContain("class=\"button is-").doesNotContain("notification is-").doesNotContain("errorMessage");
        assertThat(review).doesNotContain("confirmSave").doesNotContain("alert(").doesNotContain("style=")
                .doesNotContain("class=\"button is-").doesNotContain("bulk-create");
    }

    @Test
    void theReviewTableIsEditableAndSummarisesItsErrors() throws Exception {
        // when / then
        assertThat(source("products-add-review")).contains("cl-table is-compact is-editable")
                .contains("errorSummaryText('review-errors', ${errors})")
                .contains("ProductsBulkAddForm).fieldId");
    }

    @Test
    void everyBrandOptionStatesItsCountAndItsLabel() {
        // given
        String html = renderedProposals();

        // when
        List<String> options = tagsOf(html, "option");

        // then
        assertThat(options).hasSize(3)
                .allSatisfy(option -> assertThat(option).contains("data-count=").contains("data-label="));
    }

    @Test
    void theProposalsPostTheCheckedEansToTheReview() {
        // given
        String html = renderedProposals();

        // then
        assertThat(occurrences(html, "<form")).isEqualTo(1);
        assertThat(html).contains("action=\"/dashboard/catalogs/c1/category/k1/products/add/review\"")
                .contains("name=\"eans\"").contains("value=\"1\"").contains("value=\"2\"")
                .contains("MSI RTX 5070").contains("Acme, Elko").contains("No PIM entry")
                .contains("2 749,00 PLN").contains("data-sort-price=\"2 749,00\"")
                .contains("href=\"/dashboard/catalogs/c1/category/k1/products/new?ean=1\"")
                .doesNotContain("??");
    }

    /** Every error key is the id of the field it belongs to, so the summary links land on that field. */
    @Test
    void everyReviewErrorLinksToTheFieldItBelongsTo() {
        // given
        String html = renderedReview(Map.of("product-0-name", "Enter the name of the product."));

        // then
        assertThat(html).contains("href=\"#product-0-name\"").contains("id=\"product-0-name\"")
                .contains("name=\"products[0].name\"").contains("aria-invalid=\"true\"")
                .contains("Enter the name of the product.").doesNotContain("??");
    }

    /** The pim id is never posted back: the save resolves the entry itself, so a forged one cannot claim another. */
    @Test
    void theReviewPostsWhatItEditsButNeitherTheCategoryNorThePimEntry() {
        // given
        String html = renderedReview(Map.of());

        // then
        assertThat(html).contains("name=\"products[0].ean\"").contains("name=\"products[0].manufacturerCode\"")
                .contains("name=\"products[0].brand\"").contains("name=\"products[0].availabilityType\"")
                .contains("name=\"products[0].pricingGroup\"").contains("id=\"products\"")
                .contains("action=\"/dashboard/catalogs/c1/category/k1/products/add/save\"")
                .doesNotContain("products[0].pimId").doesNotContain("categoryId").doesNotContain("productId");
    }

    private static String renderedProposals() {
        RecommendationRow listed = new RecommendationRow("1", "MSI RTX 5070", "MSI", "MFN-1", true, "2 749,00",
                List.of("Acme", "Elko"), "5901234567890", "msi rtx 5070 1 mfn-1 msi",
                "/dashboard/catalogs/c1/category/k1/products/new?ean=1");
        RecommendationRow noPim = new RecommendationRow("2", "Zotac RTX 5080", "Zotac", "MFN-2", false, "5 199,00",
                List.of("Elko"), "", "zotac rtx 5080 2 mfn-2 zotac",
                "/dashboard/catalogs/c1/category/k1/products/new?ean=2");
        Map<String, Long> brandCounts = new LinkedHashMap<>();
        brandCounts.put("MSI", 1L);
        brandCounts.put("Zotac", 1L);
        Context context = baseContext();
        context.setVariable("rows", List.of(listed, noPim));
        context.setVariable("brandCounts", brandCounts);
        context.setVariable("hasMapping", true);
        context.setVariable("pimNames", "Graphics cards");
        context.setVariable("filtersCount", 4);
        context.setVariable("filtersHref", "/dashboard/catalogs/c1/category/k1/settings/filters");
        context.setVariable("basicsHref", "/dashboard/catalogs/c1/category/k1/settings/basics");
        context.setVariable("reviewAction", "/dashboard/catalogs/c1/category/k1/products/add/review");
        context.setVariable("newProductHref", "/dashboard/catalogs/c1/category/k1/products/new");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1");
        context.setVariable("catalogError", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/products-add", context);
    }

    private static String renderedReview(Map<String, String> errors) {
        Product product = new Product("k1", "pim-1", "5901234567890", "MFN-1", "MSI", "RTX 5070", "MSI RTX 5070", "Default");
        Context context = baseContext();
        context.setVariable("form", ProductsBulkAddForm.of(List.of(product)));
        context.setVariable("errors", errors);
        context.setVariable("labels", List.of("RTX 5060", "RTX 5070"));
        context.setVariable("pricingGroups", List.of("Default", "Premium"));
        context.setVariable("skipped", List.of());
        context.setVariable("skippedExisting", List.of());
        context.setVariable("saveAction", "/dashboard/catalogs/c1/category/k1/products/add/save");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/products/add");
        return EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);
    }

    private static Context baseContext() {
        ProductCatalog catalog = new ProductCatalog("store-1", "Parts");
        catalog.setCatalogId("c1");
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU");
        gpu.setCategoryId("k1");
        Context context = new Context();
        context.setVariable("catalog", catalog);
        context.setVariable("category", gpu);
        return context;
    }
}
