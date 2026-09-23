package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.web.catalog.InventoryFilterLabels;
import pl.commercelink.web.dtos.RecommendationFiltersForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryFiltersTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/category-filters.html"), StandardCharsets.UTF_8);
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void savesWithoutReloadAndShowsAlreadyTranslatedErrors() throws Exception {
        // when / then
        assertThat(page()).contains("th:fragment=\"filtersForm\"").contains("id=\"category-filters-form\"")
                .contains("data-cl-async").contains("data-cl-redirect=${redirectTo}").contains("@{/js/async-form.js}")
                .contains("errorSummaryText('category-filters-errors', ${errorSummary})");
    }

    @Test
    void theFiltersAreRepeatedFieldsetsAndTheirFieldsFollowTheChosenKind() throws Exception {
        // given
        String page = page();

        // then
        assertThat(page).contains("data-cl-repeat=\"filters\"").contains("data-cl-repeat-id=\"filter\"")
                .contains("data-cl-repeat-min=\"0\"").contains("data-cl-repeat-template").contains("data-cl-repeat-status")
                .contains("@{/js/repeat-fields.js}").contains("@{/js/variant-fields.js}")
                .contains("data-cl-variant-select").contains("data-cl-variant-group").contains("data-cl-variant-when");
    }

    @Test
    void carriesNoTableNoInlineStyleAndNoDeveloperHelpText() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("<table").doesNotContain("style=").doesNotContain("Comma separated");
    }

    /** The form as the controller renders it for a category with a brand filter, a price range and a by-brand filter. */
    private static String rendered(Map<String, String> errors) {
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        gpu.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.BRAND_NAME,
                List.of(new Metadata("Brands", "MSI, ASUS"), new Metadata("Legacy", "x"))));
        gpu.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.PRICE_RANGE,
                List.of(new Metadata("MinPrice", "900"), new Metadata("MaxPrice", "2147483647"))));
        gpu.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.PRODUCT_LINE_BY_BRAND,
                List.of(new Metadata("Gigabyte", "Windforce"))));
        Context context = new Context();
        context.setVariable("form", RecommendationFiltersForm.from(gpu));
        context.setVariable("errors", errors);
        context.setVariable("errorSummary", errors);
        context.setVariable("category", gpu);
        context.setVariable("filterTypes", InventoryFilterLabels.options());
        context.setVariable("listVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.LIST));
        context.setVariable("brandVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.BY_BRAND));
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/filters");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("lead", "GPU · filters");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-filters", Set.of("filtersForm"), context);
    }

    @Test
    void everyFilterPostsItsOwnFieldsAndTheKindIsChosenByName() {
        // when
        String html = rendered(Map.of());

        // then
        assertThat(html).contains("name=\"filters[0].type\"").contains("id=\"filter-0-type\"")
                .contains("name=\"filters[0].values\"").contains("value=\"MSI, ASUS\"")
                .contains("name=\"filters[1].minPrice\"").contains("value=\"900\"")
                .contains("name=\"filters[2].brandLines\"").contains("Gigabyte: Windforce")
                .contains("Selected brands only").contains("Brands, separated by commas");
        // the "no maximum" bound is an empty field, not the number the filter reads as no bound
        assertThat(html).doesNotContain("2147483647").doesNotContain("??");
        // three filters, the blank template and the row of the one unknown metadata pair
        assertThat(occurrences(html, "data-cl-repeat-remove")).isEqualTo(5);
    }

    /** Without JavaScript every kind is on the page, so the field shared by a kind keeps a label of its own kind. */
    @Test
    void theFieldOfAKindThatWasNotChosenKeepsTheLabelOfTheFirstFilterOfThatKind() {
        // when
        String html = rendered(Map.of());

        // then the second filter is a price range, so its list field shows the label of the first list filter
        assertThat(html).contains("<span data-cl-variant-when=\"filter-1-kind=BRAND_NAME\">Brands, separated by commas</span>");
    }

    /** Metadata the form does not understand is editable and removable, so a hand-made filter can be cleaned up. */
    @Test
    void metadataTheFormDoesNotUnderstandIsAnEditableRowWithAPill() {
        // when
        String html = rendered(Map.of());

        // then
        assertThat(html).contains("name=\"filters[0].unknown[0].key\"").contains("value=\"Legacy\"")
                .contains("name=\"filters[0].unknown[0].value\"").contains("id=\"filter-0-unknown-0-key\"")
                .contains("cl-status is-warn").contains("Unknown field")
                .doesNotContain("type=\"hidden\"");
    }

    /** The rows are renumbered by repeat-fields.js like any other repeated row, so a removal leaves no gap. */
    @Test
    void theUnknownRowsAreTheirOwnRepeatedGroup() throws Exception {
        // when / then
        assertThat(page()).contains("data-cl-repeat=\"unknown\"").contains("data-cl-repeat-id=\"unknown\"")
                .contains("data-cl-repeat-min=\"0\"");
    }

    @Test
    void anErrorIsShownAtItsFieldAndLinkedFromTheSummary() {
        // when
        String html = rendered(Map.of("filter-2-brandLines", "Line 2: no colon between the brand and its values."));

        // then
        assertThat(html).contains("href=\"#filter-2-brandLines\"").contains("id=\"filter-2-brandLines\"")
                .contains("Line 2: no colon between the brand and its values.");
        assertThat(occurrences(html, "cl-field-error")).isEqualTo(1);
    }

    /** Each repeated filter carries its own variant group, so one filter's kind does not switch another one's fields. */
    @Test
    void eachFilterHasItsOwnVariantGroupAndTheBlankRowCarriesTheIndexPlaceholder() throws Exception {
        // when
        String html = rendered(Map.of());

        // then
        assertThat(html).contains("data-cl-variant-select=\"filter-0-kind\"").contains("data-cl-variant-select=\"filter-2-kind\"")
                .contains("data-cl-variant-group=\"filter-1-kind\"").contains("data-cl-variant=\"PRICE_RANGE\"")
                .contains("data-cl-variant-when=\"filter-0-kind=BRAND_NAME|PRODUCT_TITLE_CONTAINS|PRODUCT_TITLE_DOES_NOT_CONTAIN|EAN_NOT_EQ\"");
        assertThat(page()).contains("filter-' + index + '-kind").contains("'@INDEX@'");
    }

    /** repeat-fields.js renumbers ids and names of a moved group; the variant group names must move with them. */
    @Test
    void theRepeatScriptRenumbersTheVariantAttributesToo() throws Exception {
        // given
        String script = Files.readString(Path.of("src/main/resources/static/js/repeat-fields.js"), StandardCharsets.UTF_8);

        // then
        assertThat(script).contains("data-cl-variant-select").contains("data-cl-variant-group").contains("data-cl-variant-when");
    }

    /** The page as the controller renders it, header included, for a category with the given name. */
    private static String renderedPage(String categoryName, Integer matchingProducts) {
        CategoryDefinition category = new CategoryDefinition().withName(categoryName).withGeneratedId();
        Context context = new Context();
        context.setVariable("form", RecommendationFiltersForm.from(category));
        context.setVariable("errors", Map.of());
        context.setVariable("errorSummary", Map.of());
        context.setVariable("category", category);
        context.setVariable("filterTypes", InventoryFilterLabels.options());
        context.setVariable("listVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.LIST));
        context.setVariable("brandVariants", InventoryFilterLabels.variants(InventoryFilterLabels.Kind.BY_BRAND));
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/filters");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("productsAddHref", "/dashboard/catalogs/c1/category/k1/products/add");
        if (matchingProducts != null) {
            context.setVariable("matchingProducts", matchingProducts);
        }
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-filters", context);
    }

    /**
     * The lead is a block of the template, not HTML put together in the controller and printed unescaped: a category
     * name with markup is shown as text, and the count links to the page that adds the products (D-M50, D-C3).
     */
    @Test
    void theLeadShowsTheCategoryNameAsTextAndLinksTheCountToAddingProducts() {
        // when
        String html = renderedPage("<b>GPU</b>", 8);

        // then
        String lead = html.substring(html.indexOf("<p class=\"cl-page-lead\">"), html.indexOf("</p>", html.indexOf("<p class=\"cl-page-lead\">")));
        assertThat(lead).contains("&lt;b&gt;GPU&lt;/b&gt; · narrow down the products suggested from the inventory.")
                .doesNotContain("<b>")
                .contains("<a href=\"/dashboard/catalogs/c1/category/k1/products/add\">To add today: 8</a>");
    }

    @Test
    void theLeadLeavesTheCountOutWhenItIsUnknown() {
        // when
        String html = renderedPage("GPU", null);

        // then
        assertThat(html).contains("GPU · narrow down the products suggested from the inventory.")
                .doesNotContain("To add today");
    }
}
