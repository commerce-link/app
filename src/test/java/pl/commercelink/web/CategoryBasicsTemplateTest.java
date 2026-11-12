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
    void thePimCategoriesGoThroughTheSharedPicker() throws Exception {
        // when / then
        assertThat(page()).contains("category-picker :: multiPicker('pimCategoryIds'")
                .contains("category-picker :: multiPickerScript(${categoryOptions}, ${categoryAncestors})");
    }

    /** The page as the controller renders it for an existing, unprotected category with one label. */
    private static String rendered() {
        return renderedFor(new CategoryDefinition().withName("GPU").withGeneratedId());
    }

    private static String renderedFor(CategoryDefinition gpu) {
        return renderedFor(gpu, true);
    }

    private static String renderedFor(CategoryDefinition gpu, boolean existing) {
        CategoryBasicsForm form = CategoryBasicsForm.from(gpu);
        form.setLabels(List.of("RTX 5060"));
        return render(context(gpu, existing, form, Map.of()));
    }

    /** The page of an automatic category whose list holds {@code listed} products, {@code kept} of them already rows. */
    private static String renderedAutomatic(int listed, int kept, CategoryBasicsForm form, Map<String, String> errors) {
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        gpu.setType(CategoryDefinitionType.Dynamic);
        Context context = context(gpu, true, form, errors);
        context.setVariable("dynamicListCount", listed);
        context.setVariable("keptProductsCount", kept);
        return render(context);
    }

    private static CategoryBasicsForm automaticForm() {
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId();
        gpu.setType(CategoryDefinitionType.Dynamic);
        return CategoryBasicsForm.from(gpu);
    }

    private static String render(Context context) {
        return EnglishFragmentTemplateEngine.create().process("catalog/category-basics", context);
    }

    private static Context context(CategoryDefinition gpu, boolean existing, CategoryBasicsForm form, Map<String, String> errors) {
        Context context = new Context();
        context.setVariables(Map.of(
                "form", form,
                "errors", errors,
                "existing", existing,
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
        return context;
    }

    /** The radios of the automatic list's choice, as rendered. */
    private static List<String> dynamicProductsRadios(String html) {
        return Pattern.compile("<input[^>]*name=\"dynamicProducts\"[^>]*>").matcher(html).results()
                .map(java.util.regex.MatchResult::group).toList();
    }

    @Test
    void anAutomaticCategoryAsksWhatToDoWithItsListWhenTurnedManual() {
        // when
        String html = renderedAutomatic(12, 0, automaticForm(), Map.of());

        // then
        assertThat(html).contains("Products on the automatic list: 12. What should happen to them after the change to Manual?")
                .contains("Save them in the category").contains("Do not save")
                .doesNotContain("Products already in the category stay unchanged");
        assertThat(dynamicProductsRadios(html)).hasSize(2).noneMatch(radio -> radio.contains("checked"));
        assertThat(html).containsPattern("<fieldset[^>]*id=\"dynamicProducts\"[^>]*data-cl-variant-when=\"categoryType=Managed\"");
    }

    @Test
    void theChoiceSaysHowManyProductsTheCategoryKeeps() {
        // when
        String html = renderedAutomatic(12, 3, automaticForm(), Map.of());

        // then
        assertThat(html).contains("Products already in the category stay unchanged: 3.");
    }

    @Test
    void anEmptyAutomaticListOnlyInforms() {
        // when
        String html = renderedAutomatic(0, 0, automaticForm(), Map.of());

        // then
        assertThat(html).contains("The automatic list is empty");
        assertThat(dynamicProductsRadios(html)).isEmpty();
    }

    @Test
    void aRefusedSaveKeepsTheChoiceAndShowsTheErrorOfAMissingOne() {
        // given
        CategoryBasicsForm chosen = automaticForm();
        chosen.setDynamicProducts(CategoryBasicsForm.STORE_DYNAMIC_PRODUCTS);

        // when
        String keptChoice = renderedAutomatic(12, 0, chosen, Map.of("name", "catalog.category.name.required"));
        String missingChoice = renderedAutomatic(12, 0, automaticForm(),
                Map.of("dynamicProducts", "catalog.category.type.managed.choice.required"));

        // then
        assertThat(dynamicProductsRadios(keptChoice)).filteredOn(radio -> radio.contains("checked"))
                .singleElement().satisfies(radio -> assertThat(radio).contains("value=\"store\""));
        assertThat(missingChoice).contains("Choose whether to save the products of the automatic list in the category.");
    }

    @Test
    void aManualCategoryAsksNothingAboutAnAutomaticList() {
        // when
        String html = rendered();

        // then
        assertThat(dynamicProductsRadios(html)).isEmpty();
        assertThat(html).doesNotContain("The automatic list is empty");
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

    /**
     * RF-31: an empty position puts a new category at the end, while on a saved one it keeps the position it has;
     * the help says which of the two the field does.
     */
    @Test
    void thePositionHelpSaysWhatAnEmptyFieldDoesOnThisForm() {
        // when
        String edited = renderedFor(new CategoryDefinition().withName("GPU").withGeneratedId(), true);
        String created = renderedFor(new CategoryDefinition().withName("GPU").withGeneratedId(), false);

        // then
        assertThat(edited).contains("Empty = the position stays as it is.").doesNotContain("Empty = at the end.");
        assertThat(created).contains("Empty = at the end.").doesNotContain("Empty = the position stays as it is.");
    }
}
