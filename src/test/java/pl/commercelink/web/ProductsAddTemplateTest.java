package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.web.catalog.RecommendationRow;
import pl.commercelink.web.dtos.ProductsBulkAddForm;
import pl.commercelink.web.inventory.CatalogTargetOptions;
import pl.commercelink.web.inventory.InventoryAddController;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
                .contains("data-cl-selection-bar").contains("data-cl-selection-count").doesNotContain("data-cl-select-clear")
                .contains("data-cl-label-select=").contains("data-cl-label-clear=")
                .contains("data-cl-select-table=\"true\"").contains("data-cl-select-all").contains("data-cl-select-row")
                .contains("@{/js/table-filter.js}").contains("@{/js/table-sort.js}").contains("@{/js/table-select.js}");
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

    /** The checkbox is 18 px; the label around it is the touch target (44 px below 1024 px, D-I5). */
    @Test
    void everyProposalCheckboxSitsInALabelThatIsItsTouchTarget() {
        // when
        String html = renderedProposals();

        // then
        // two rows, the header's "select visible" and the selection row's own
        assertThat(occurrences(html, "<label class=\"cl-check-target\">")).isEqualTo(4);
        assertThat(html).containsPattern("<label class=\"cl-check-target\">\\s*<input type=\"checkbox\" class=\"cl-check-input\" data-cl-select-all")
                .containsPattern("<label class=\"cl-check-target\">\\s*<input type=\"checkbox\" class=\"cl-check-input\" name=\"eans\"");
    }

    /** Both codes of a proposal are kept whole: an EAN and a manufacturer code never break inside. */
    @Test
    void theEanAndTheManufacturerCodeOfAProposalAreCodes() {
        // when
        String html = renderedProposals();

        // then
        assertThat(html).contains("<span class=\"cl-table-code\">EAN 1</span>")
                .contains("<span> · <span class=\"cl-table-code\">MFN-1</span></span>");
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

    /**
     * A field in error points at its message (aria-describedby = the id of the message), so a screen reader reads why
     * the field is invalid; the summary above the table carries the numbered lines the controller gives it.
     */
    @Test
    void everyFieldInErrorIsDescribedByItsMessage() {
        // given
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("product-0-ean", "Enter an EAN or a manufacturer code.");
        errors.put("product-0-manufacturerCode", "Enter an EAN or a manufacturer code.");
        errors.put("product-0-name", "Enter the name of the product.");
        errors.put("product-0-label", "Choose a label from the list of the category.");
        errors.put("product-0-pricingGroup", "Choose a pricing group of the category.");
        Map<String, String> summary = new LinkedHashMap<>();
        errors.forEach((field, text) -> summary.put(field, "Product 1: " + text));

        // when
        String html = renderedReview(errors, summary);

        // then
        for (String field : errors.keySet()) {
            String control = tagsOf(html, "(?:input|select)").stream().filter(tag -> tag.contains("id=\"" + field + "\""))
                    .findFirst().orElseThrow();
            assertThat(control).as(field).contains("aria-invalid=\"true\"").contains("aria-describedby=\"" + field + "-error\"");
            assertThat(html).as(field).containsPattern("<p [^>]*id=\"" + field + "-error\"[^>]*>");
        }
        assertThat(html).contains(">Product 1: Enter the name of the product.</a>")
                .doesNotContain(">Product 1: Enter the name of the product.</p>");
    }

    @Test
    void aFieldWithoutAnErrorIsDescribedByNothing() {
        // when
        String html = renderedReview(Map.of());

        // then
        assertThat(html).doesNotContain("aria-describedby").doesNotContain("-error\"");
    }

    /** A placeholder is gone once the field holds a value; the two identifiers are told apart by a visible label. */
    @Test
    void theIdentifierFieldsHaveVisibleLabels() {
        // when
        String html = renderedReview(Map.of());

        // then
        assertThat(html).containsPattern("<label class=\"cl-label\" for=\"product-0-ean\">EAN</label>")
                .containsPattern("<label class=\"cl-label\" for=\"product-0-manufacturerCode\">[^<]+</label>");
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

    /**
     * The identifiers decide which PIM entry the product resolves to, and a proposal can carry the wrong one; the
     * review is the last place to correct it, so both are text fields with an error slot of their own.
     */
    @Test
    void theReviewEditsTheEanAndTheManufacturerCode() {
        // given
        String html = renderedReview(Map.of("product-0-ean", "An EAN is 8\u201314 digits."));

        // then
        assertThat(html).contains("id=\"product-0-ean\"").contains("id=\"product-0-manufacturerCode\"")
                .contains("href=\"#product-0-ean\"").contains("An EAN is 8\u201314 digits.")
                .contains("value=\"5901234567890\"").contains("value=\"MFN-1\"").contains(">MSI<")
                .doesNotContain("type=\"hidden\" name=\"products[0].ean\"")
                .doesNotContain("type=\"hidden\" name=\"products[0].manufacturerCode\"")
                .doesNotContain("??");
    }

    /** The currency of a proposal's price is a message; the selection is announced from a region always in the page. */
    @Test
    void theCurrencyIsAMessageAndTheSelectionIsAnnouncedFromAPermanentRegion() throws Exception {
        // when
        String html = renderedProposals();

        // then
        assertThat(source("products-add")).doesNotContain("PLN");
        assertThat(html).contains("2 749,00 PLN")
                .containsPattern("<p class=\"cl-visually-hidden\" role=\"status\" data-cl-selection-status></p>\\s*<div class=\"cl-selection-row\"");
        String bar = html.substring(html.indexOf("data-cl-selection-bar"), html.indexOf("cl-selection-actions"));
        assertThat(bar).doesNotContain("role=\"status\"");
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
        return renderedReview(errors, errors);
    }

    private static String renderedReview(Map<String, String> errors, Map<String, String> errorSummary) {
        Product product = new Product("k1", "pim-1", "5901234567890", "MFN-1", "MSI", "RTX 5070", "MSI RTX 5070", "Default");
        Context context = baseContext();
        context.setVariable("form", ProductsBulkAddForm.of(List.of(product)));
        context.setVariable("errors", errors);
        context.setVariable("errorSummary", errorSummary);
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

    /** RF-6: the review posts back the id it was shown with, so the same review sent twice saves each row once. */
    @Test
    void theReviewPostsItsOwnId() {
        // when
        String html = renderedReview(Map.of());

        // then
        assertThat(html).containsPattern("<input type=\"hidden\" name=\"reviewId\" value=\"[0-9a-f-]{36}\"");
    }

    /** The back link is named after the page it leads to: the supplier assortment when the review came from there. */
    @Test
    void aReviewFromTheInventoryNamesItsBackLinkAfterTheInventory() {
        // given
        Context context = baseContext();
        context.setVariable("form", ProductsBulkAddForm.of(List.of()));
        context.setVariable("errors", Map.of());
        context.setVariable("errorSummary", Map.of());
        context.setVariable("labels", List.of());
        context.setVariable("pricingGroups", List.of("Default"));
        context.setVariable("skipped", List.of());
        context.setVariable("skippedExisting", List.of());
        context.setVariable("saveAction", "/dashboard/catalogs/c1/category/k1/products/add/save");
        context.setVariable("backHref", "/dashboard/inventory?cat=11");
        context.setVariable("returnTo", "/dashboard/inventory?cat=11");

        // when
        String fromInventory = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);
        String fromCatalog = renderedReview(Map.of());

        // then
        assertThat(fromInventory).contains("Supplier assortment");
        assertThat(fromCatalog).doesNotContain("Supplier assortment");
    }

    /** From the inventory the save needs the way back and the count of products the review already dropped. */
    @Test
    void aReviewFromTheInventoryPostsTheWayBackAndWhatItSkipped() {
        // given
        Context context = baseContext();
        context.setVariable("form", ProductsBulkAddForm.of(List.of()));
        context.setVariable("errors", Map.of());
        context.setVariable("errorSummary", Map.of());
        context.setVariable("labels", List.of());
        context.setVariable("pricingGroups", List.of("Default"));
        context.setVariable("skipped", List.of());
        context.setVariable("skippedExisting", List.of("5901234567890"));
        context.setVariable("saveAction", "/dashboard/catalogs/c1/category/k1/products/add/save");
        context.setVariable("backHref", "/dashboard/inventory?cat=11");
        context.setVariable("returnTo", "/dashboard/inventory?cat=11");
        context.setVariable("skippedBefore", 1);

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);

        // then
        assertThat(html).contains("<input type=\"hidden\" name=\"returnTo\" value=\"/dashboard/inventory?cat=11\"/>")
                .contains("<input type=\"hidden\" name=\"skippedBefore\" value=\"1\"/>");
    }

    @Test
    void aReviewFromTheCatalogPostsNoSkippedCount() {
        // when
        String html = renderedReview(Map.of());

        // then
        assertThat(html).doesNotContain("name=\"skippedBefore\"");
    }

    /** From the catalog the category is the one of the address: named for the operator, never a field to post. */
    @Test
    void aReviewFromTheCatalogShowsItsCategoryReadOnly() {
        // when
        String html = renderedReview(Map.of());

        // then
        assertThat(html).contains("<p class=\"cl-help\">Catalog category: Parts › GPU</p>");
        assertThat(html).doesNotContain("name=\"target\"", "review-target", "reviewedTarget", "Change category", "cl-stack");
    }

    /**
     * From the inventory the category is the first field: a combobox over the native select it enhances -- one flat list,
     * the matching categories first, the catalog's name on a grey line under each. Without the script
     * the select and "Zmień kategorię" beside it do the job; nothing is sent when the select changes (WCAG 3.2.2).
     */
    @Test
    void aReviewFromTheInventoryAsksForTheCategoryAboveTheRows() {
        // given
        Context context = inventoryContext(new CatalogTargetOptions(2,
                List.of(new CatalogTargetOptions.Option("c1", "k1", "Parts", "GPU", 1)),
                List.of(new CatalogTargetOptions.Option("c2", "k2", "Garden", "Tools", 0)),
                "c1/k1", false, "Graphics cards"), "c1/k1", Map.of());

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);

        // then
        assertThat(html).doesNotContain("??", "Catalog category: Parts");
        assertThat(html).contains("<label class=\"cl-label\" id=\"review-target-label\" for=\"review-target\">Catalog category</label>");
        assertThat(html).containsPattern("<div class=\"cl-input-row\">\\s*<div class=\"cl-picker cl-combobox\" data-cl-combobox>\\s*"
                + "<select class=\"cl-select\" id=\"review-target\" name=\"target\" data-combobox-select");
        // the order of the processed attributes is not fixed
        assertThat(tagsOf(html, "select").get(0)).contains("aria-describedby=\"review-target-help\"", "required=\"required\"",
                "autofocus=\"autofocus\"");
        assertThat(html).containsPattern("</div>\\s*<button class=\"cl-button\" type=\"submit\" formnovalidate data-review-change\\s+"
                + "formaction=\"/dashboard/inventory/add\">Change category</button>\\s*</div>");
        assertThat(html).containsPattern("<option value=\"c1/k1\"\\s+selected=\"selected\">GPU — Parts · matches</option>");
        assertThat(html).contains("<option value=\"c2/k2\">Tools — Garden</option>",
                "<span class=\"cl-picker-name\">GPU</span>", "<span class=\"cl-picker-meta\">Parts · matches</span>",
                "<span class=\"cl-picker-meta\">Garden</span>", "value=\"GPU\"",
                "PIM category: Graphics cards. Manual categories only",
                "<input type=\"hidden\" name=\"ean\" value=\"5901234567890\"/>", "<input type=\"hidden\" name=\"ean\" value=\"5901234567891\"/>",
                "<input type=\"hidden\" name=\"reviewedTarget\" value=\"c1/k1\"/>",
                "<input type=\"hidden\" name=\"products[0].sourceEan\" value=\"5901234567890\">",
                "<script src=\"/js/combobox.js\" defer></script>", "<script src=\"/js/review-target.js\" defer></script>",
                "data-review-status=\"Category: Parts › GPU. Rows: 1.\"",
                ">Add</button>");
        assertThat(html).doesNotContain("Next</button>", "Add products", "optgroup", "already here");
        assertThat(html.indexOf("id=\"review-target\"")).isLessThan(html.indexOf("id=\"review-area\""));
        assertThat(html.indexOf("id=\"review-area\"")).isLessThan(html.indexOf("id=\"products\""));
        assertThat(html).contains("<div class=\"cl-stack is-wide\">");
    }

    /** The rows, their notes and what the save needs about them form the part a pick in the combobox redraws. */
    @Test
    void theRedrawnPartCarriesTheRowsTheirNotesAndTheReviewedCategory() {
        // given
        Context context = inventoryContext(new CatalogTargetOptions(2,
                List.of(new CatalogTargetOptions.Option("c1", "k1", "Parts", "GPU", 1)), List.of(), "c1/k1", false, null),
                "c1/k1", Map.of());
        context.setVariable("skippedExisting", List.of("5901234567891"));
        context.setVariable("skippedBefore", 1);
        context.setVariable("partial", true);

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review-parts", Set.of("reviewArea"), context);

        // then
        assertThat(html).startsWith("<div class=\"cl-stack is-wide\" id=\"review-area\" data-review-area");
        assertThat(html).contains("data-review-status=\"Category: Parts › GPU. Rows: 1. Skipped: 1.\"",
                "<input type=\"hidden\" name=\"reviewedTarget\" value=\"c1/k1\"/>",
                "<input type=\"hidden\" name=\"skippedBefore\" value=\"1\"/>",
                "already in this category: 5901234567891", "id=\"products\"", ">Add</button>");
        assertThat(html).doesNotContain("review-target-label", "data-cl-combobox", "name=\"ean\"", "name=\"reviewId\"", "hidden=\"hidden\"");
    }

    /**
     * Nothing matched: the field starts empty and says why; there are no rows to fill in until a category is chosen.
     * Without the script "Next" -- not a change of the select -- draws them; with it, a pick does.
     */
    @Test
    void anInventoryReviewWithoutACategoryHasOnlyTheFieldWithNextAsItsMainButton() {
        // given
        Context context = inventoryContext(new CatalogTargetOptions(1, List.of(),
                List.of(new CatalogTargetOptions.Option("c1", "k2", "Parts", "Cases", 0)), null, false, null), "", Map.of());
        context.setVariable("category", null);
        context.setVariable("catalog", null);
        context.setVariable("form", ProductsBulkAddForm.of(List.of()));

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);

        // then
        assertThat(html).doesNotContain("??", "id=\"products\"", ">Add</button>", "reviewedTarget", "matches",
                ">Change category</button>", "cl-input-row", "data-review-status=");
        assertThat(html).containsPattern("<option value=\"\"\\s+selected=\"selected\">Choose a category…</option>");
        assertThat(html).contains("placeholder=\"Choose a category…\"",
                "<input type=\"hidden\" name=\"target\" value=\"\" disabled data-combobox-value>",
                "<option value=\"c1/k2\">Cases</option>");
        // one catalog: no catalog line under the options, and nothing matches, so no grey line at all
        assertThat(html).doesNotContain("cl-picker-meta", "Cases — Parts");
        assertThat(tagsOf(html, "input").stream().filter(tag -> tag.contains("data-combobox-input")).findFirst().orElseThrow())
                .contains("required=\"required\"", "value=\"\"");
        assertThat(html).contains(
                "This product&#39;s PIM category is not mapped to any catalog category.",
                "<a class=\"cl-button\" href=\"/dashboard/inventory?cat=11\">Cancel</a>",
                "Complete the data: 1 products");
        assertThat(html).containsPattern("<button class=\"cl-button is-primary\" type=\"submit\" formnovalidate data-review-next\\s+formaction=\"/dashboard/inventory/add\">Next</button>");
        assertThat(tagsOf(html, "div").stream().filter(tag -> tag.contains("data-review-area")).findFirst().orElseThrow())
                .contains("id=\"review-area\"", "hidden=\"hidden\"");
    }

    /** A category the store does not have (or no longer offers) is an error of the field, linked from the summary. */
    @Test
    void anUnknownCategoryIsAnErrorOfTheField() {
        // given
        Map<String, String> errors = Map.of("review-target", "No catalog category was chosen.");
        Context context = inventoryContext(new CatalogTargetOptions(1, List.of(), List.of(), null, false, null), "", errors);
        context.setVariable("category", null);
        context.setVariable("form", ProductsBulkAddForm.of(List.of()));

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);

        // then
        assertThat(html).contains("<a href=\"#review-target\">No catalog category was chosen.</a>",
                "aria-invalid=\"true\"", "aria-describedby=\"review-target-error review-target-help\"",
                "<p class=\"cl-field-error\" id=\"review-target-error\">");
        assertThat(html).doesNotContain("autofocus");
    }

    @Test
    void aStoreWithoutManualCategoriesIsSentToTheCatalogsInsteadOfAForm() {
        // given
        Context context = inventoryContext(new CatalogTargetOptions(1, List.of(), List.of(), null, true, null), "", Map.of());
        context.setVariable("category", null);

        // when
        String html = EnglishFragmentTemplateEngine.create().process("catalog/products-add-review", context);

        // then
        assertThat(html).contains("There is no manual category in your product catalogs.",
                "<a href=\"/dashboard/catalogs\">Go to product catalogs</a>");
        assertThat(html).doesNotContain("<form", "review-target");
    }

    private static MessageSource englishMessages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }

    private static Context inventoryContext(CatalogTargetOptions options, String selected, Map<String, String> errors) {
        Product product = new Product("k1", "pim-1", "5901234567890", "MFN-1", "MSI", "RTX 5070", "MSI RTX 5070", "Default");
        ProductsBulkAddForm form = ProductsBulkAddForm.of(List.of(product));
        form.getProducts().get(0).setSourceEan("5901234567890");
        Context context = baseContext();
        context.setVariable("form", form);
        context.setVariable("errors", errors);
        context.setVariable("errorSummary", errors);
        context.setVariable("labels", List.of());
        context.setVariable("pricingGroups", List.of("Default"));
        context.setVariable("skipped", List.of());
        context.setVariable("skippedExisting", List.of());
        context.setVariable("saveAction", "/dashboard/inventory/add/save");
        context.setVariable("changeAction", "/dashboard/inventory/add");
        context.setVariable("backHref", "/dashboard/inventory?cat=11");
        context.setVariable("returnTo", "/dashboard/inventory?cat=11");
        context.setVariable("skippedBefore", 0);
        context.setVariable("resetRows", 0);
        context.setVariable("targetOptions", options);
        context.setVariable("targetChoices", InventoryAddController.targetOptions(options, englishMessages(), Locale.ENGLISH));
        context.setVariable("selectedTarget", selected);
        context.setVariable("eans", List.of("5901234567890", "5901234567891"));
        context.setVariable("reviewCount", options.count());
        return context;
    }
}
