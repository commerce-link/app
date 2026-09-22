package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.web.catalog.CategoryTypeLabels;
import pl.commercelink.web.dtos.CategoryBasicsForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryBasicsTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/category-basics.html"), StandardCharsets.UTF_8);
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void savesWithoutReloadAndAsksTheServerAgainOnAnError() throws Exception {
        // when / then
        assertThat(page()).contains("th:fragment=\"basicsForm\"").contains("id=\"category-basics-form\"").contains("data-cl-async")
                .contains("data-cl-redirect=${redirectTo}").contains("@{/js/async-form.js}")
                .contains("errorSummary('category-basics-errors'");
    }

    @Test
    void theTypeChoiceIsRadiosAndTheWarningFollowsThem() throws Exception {
        // when / then
        assertThat(page()).contains("data-cl-variant-select=\"categoryType\"").contains("data-cl-variant-when=\"categoryType=Dynamic\"")
                .contains("@{/js/variant-fields.js}").doesNotContain("<select th:field=\"*{type}\"");
    }

    @Test
    void theLabelsAreARepeatedLineWithATemplateAndANoscriptSpare() throws Exception {
        // given
        String page = page();

        // then
        assertThat(page).contains("data-cl-repeat=\"labels\"").contains("data-cl-repeat-template").contains("<noscript>")
                .contains("data-cl-repeat-status").contains("@{/js/repeat-fields.js}");
    }

    @Test
    void thePimCategoriesGoThroughTheSharedPicker() throws Exception {
        // when / then
        assertThat(page()).contains("category-picker :: multiPicker('pimCategoryIds'")
                .contains("category-picker :: multiPickerScript(${categoryOptions}, ${categoryAncestors})");
    }

    @Test
    void carriesNoInlineStyleAndNoIdFields() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("style=").doesNotContain("categoryId\"").doesNotContain("storeId");
    }

    /** The page as the controller renders it for an existing, unprotected category with one label. */
    private static String rendered() {
        return renderedFor(new CategoryDefinition().withName("GPU").withGeneratedId());
    }

    private static String renderedFor(CategoryDefinition gpu) {
        CategoryBasicsForm form = CategoryBasicsForm.from(gpu);
        form.setLabels(List.of("RTX 5060"));
        Context context = new Context();
        context.setVariables(Map.of(
                "form", form,
                "errors", Map.of(),
                "existing", true,
                "category", gpu,
                "categoryOptions", List.of(),
                "categoryAncestors", List.of(),
                "selectedCategoryOptions", List.of(),
                "types", Arrays.stream(CategoryDefinitionType.values())
                        .map(type -> Map.of("value", type.name(), "labelKey", CategoryTypeLabels.labelKey(type),
                                "descKey", CategoryTypeLabels.descriptionKey(type)))
                        .toList()));
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/basics");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("pageTitle", "Basics");
        context.setVariable("lead", "GPU · basics");
        context.setVariable("managedProductsCount", 4);
        context.setVariable("labelsOutsideCount", 1L);
        context.setVariable("deleteHref", "/dashboard/catalogs/c1/category/k1/delete");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-basics", context);
    }

    @Test
    void theHeaderCarriesOneTitleAndTheDeleteLinkOnce() {
        // when
        String html = rendered();

        // then
        assertThat(occurrences(html, "<h1")).isEqualTo(1);
        assertThat(occurrences(html, "/dashboard/catalogs/c1/category/k1/delete")).isEqualTo(1);
        assertThat(html.indexOf("cl-page-actions")).isLessThan(html.indexOf("/dashboard/catalogs/c1/category/k1/delete"));
        assertThat(html).doesNotContain("??");
    }

    /** Support is given a category id over the phone; the Basics page must show it, as text rather than as a field. */
    @Test
    void theCategoryIdStandsUnderThePageTitleAsPlainText() {
        // given
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();

        // when
        String html = renderedFor(gpu);

        // then
        assertThat(html).contains("ID: " + gpu.getCategoryId()).doesNotContain("readonly");
        // one placement across the catalog screens: in the header, after the title block, before the form
        assertThat(html.indexOf("cl-page-title")).isLessThan(html.indexOf("ID: " + gpu.getCategoryId()));
        assertThat(html.indexOf("ID: " + gpu.getCategoryId())).isLessThan(html.indexOf("<form"));
    }

    @Test
    void theCategoryIdLineIsLeftOutOfTheNewCategoryForm() throws Exception {
        // when / then
        assertThat(page()).contains("th:if=\"${existing}\"");
    }

    @Test
    void theLabelAndTheTypeChoiceAreRenderedWithTheirValues() {
        // when
        String html = rendered();

        // then
        assertThat(html).contains("value=\"RTX 5060\"").contains("name=\"labels[0]\"");
        assertThat(occurrences(html, "type=\"radio\"")).isEqualTo(2);
        assertThat(html).contains("value=\"Managed\"").contains("value=\"Dynamic\"").contains("Manual").contains("Automatic");
    }
}
