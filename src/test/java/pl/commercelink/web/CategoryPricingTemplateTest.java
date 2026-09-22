package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.web.dtos.CategoryPricingForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryPricingTemplateTest {

    private static String page() throws Exception {
        return Files.readString(Path.of("src/main/resources/templates/catalog/category-pricing.html"), StandardCharsets.UTF_8);
    }

    private static int occurrences(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void savesWithoutReloadAndAsksTheServerAgainOnAnError() throws Exception {
        // when / then
        assertThat(page()).contains("th:fragment=\"pricingForm\"").contains("id=\"category-pricing-form\"")
                .contains("data-cl-async").contains("data-cl-redirect=${redirectTo}").contains("@{/js/async-form.js}")
                .contains("errorSummary('category-pricing-errors'");
    }

    @Test
    void thePriceGroupsAreRepeatedFieldsetsKeptAtOne() throws Exception {
        // given
        String page = page();

        // then
        assertThat(page).contains("data-cl-repeat=\"groups\"").contains("data-cl-repeat-min=\"1\"")
                .contains("data-cl-repeat-template").contains("data-cl-repeat-status").contains("@{/js/repeat-fields.js}");
    }

    @Test
    void theDefaultGroupKeepsItsNameAndTheMatchingRulesAreFolded() throws Exception {
        // when / then
        assertThat(page()).contains("th:readonly=\"${isDefault}\"").contains("cl-disclosure");
    }

    @Test
    void carriesNoTableAndNoInlineStyle() throws Exception {
        // when / then
        assertThat(page()).doesNotContain("<table").doesNotContain("style=");
    }

    /**
     * The form as the controller renders it for a category with the Default group and one matched group. Only the
     * fragment: the page file also declares the priceGroup fragment, which the layout drops but a bare engine keeps.
     */
    private static String rendered(Map<String, String> errors) {
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withStockDefinition(new StockDefinition(1, 10, 30))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"))
                .withPriceDefinition(new PriceDefinition(1.08, 99, 30, 20, 10, "Premium"));
        gpu.getPriceDefinitions().get(1).setLabelMatch("RTX 5070");
        gpu.getPriceDefinitions().get(1).setPriceMatch(2500);
        Context context = new Context();
        context.setVariable("form", CategoryPricingForm.from(gpu));
        context.setVariable("errors", errors);
        context.setVariable("category", gpu);
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/pricing");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("lead", "GPU · pricing");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-pricing", Set.of("pricingForm"), context);
    }

    @Test
    void everyGroupPostsItsOwnFieldsAndOnlyTheDefaultOneIsLocked() {
        // when
        String html = rendered(Map.of());

        // then
        assertThat(html).contains("name=\"groups[0].name\"").contains("value=\"Default\"")
                .contains("name=\"groups[1].multiplier\"").contains("value=\"1,08\"")
                .contains("name=\"groups[1].priceMatch\"").contains("value=\"2 500,00\"")
                .contains("name=\"groups[1].labelMatch\"").contains("value=\"RTX 5070\"");
        assertThat(occurrences(html, "readonly=\"readonly\"")).isEqualTo(1);
        assertThat(occurrences(html, "data-cl-repeat-remove")).isEqualTo(2);
        assertThat(html).doesNotContain("??");
    }

    @Test
    void anErrorOfOneGroupIsShownAtItsOwnField() {
        // when
        String html = rendered(Map.of("groups[1].multiplier", "catalog.category.pricing.multiplier.invalid"));

        // then
        assertThat(html).contains("The multiplier must be greater than 0").contains("id=\"group-1-multiplier-error\"");
        assertThat(occurrences(html, "cl-field-error")).isEqualTo(1);
    }
}
