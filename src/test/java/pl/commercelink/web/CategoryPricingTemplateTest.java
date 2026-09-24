package pl.commercelink.web;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.MonitoryPricingFixture;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.StockDefinition;
import pl.commercelink.web.dtos.CategoryPricingForm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                .contains("errorSummaryWithArguments('category-pricing-errors'");
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
                // N2: priceMatch is formatted like the multiplier now (FormNumbers.formatDecimalField), no grouping space.
                .contains("name=\"groups[1].priceMatch\"").contains("value=\"2500,00\"")
                .contains("name=\"groups[1].labelMatch\"").contains("value=\"RTX 5070\"");
        assertThat(occurrences(html, "readonly=\"readonly\"")).isEqualTo(1);
        assertThat(occurrences(html, "data-cl-repeat-remove")).isEqualTo(2);
        assertThat(html).doesNotContain("??");
    }

    /** Which group wins is the order of the list, so the page says the rule and each group shows what it matches. */
    @Test
    void theGroupsSayHowTheyAreMatchedAndInWhatOrder() {
        // when
        String html = rendered(Map.of());

        // then
        assertThat(html).contains("the first group from the top");
        // N2: the legend reuses the same lossless field value as the input, so it reads without a grouping space.
        assertThat(html).contains("subcategory RTX 5070 \u00b7 price from 2500,00 PLN");
        // the Default pill and the summary of the one matched group, both in the legends
        assertThat(occurrences(html, "cl-status is-neutral")).isEqualTo(1);
        assertThat(occurrences(html, "cl-status is-info")).isEqualTo(1);
    }

    /** The form as the controller renders it for a group that has only a price threshold, which assigns nothing. */
    private static String renderedWithAPriceOnlyGroup() {
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withStockDefinition(new StockDefinition(1, 10, 30))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"))
                .withPriceDefinition(new PriceDefinition(1.08, 99, 30, 20, 10, "Premium"));
        gpu.getPriceDefinitions().get(1).setPriceMatch(2500);
        Context context = new Context();
        context.setVariable("form", CategoryPricingForm.from(gpu));
        context.setVariable("errors", Map.of());
        context.setVariable("category", gpu);
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/pricing");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("lead", "GPU \u00b7 pricing");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-pricing", Set.of("pricingForm"), context);
    }

    @Test
    void aPriceThresholdWithoutALabelIsFlaggedInTheLegendInsteadOfReadingLikeARule() {
        // when
        String html = renderedWithAPriceOnlyGroup();

        // then
        assertThat(html).contains("price threshold without a subcategory");
        assertThat(occurrences(html, "cl-status is-warn")).isEqualTo(1);
        assertThat(occurrences(html, "cl-status is-neutral")).isEqualTo(0);
        assertThat(html).doesNotContain("??");
    }

    @Test
    void anErrorOfOneGroupIsShownAtItsOwnField() {
        // when
        String html = rendered(Map.of("group-1-multiplier", "catalog.category.pricing.multiplier.invalid"));

        // then
        assertThat(html).contains("The multiplier must be greater than 0").contains("id=\"group-1-multiplier-error\"");
        assertThat(occurrences(html, "cl-field-error")).isEqualTo(1);
        // the summary link and the field it names, so the error key doubles as the id repeat-fields.js renumbers
        assertThat(html).contains("href=\"#group-1-multiplier\"").contains("id=\"group-1-multiplier\"");
    }

    private static String rendered(CategoryPricingForm form, Map<String, String> errors) {
        Context context = new Context();
        context.setVariable("form", form);
        context.setVariable("errors", errors);
        context.setVariable("formAction", "/dashboard/catalogs/c1/category/k1/settings/pricing");
        context.setVariable("backHref", "/dashboard/catalogs/c1/category/k1/settings");
        context.setVariable("backLabel", "Category settings");
        context.setVariable("lead", "Monitory \u00b7 pricing");
        context.setVariable("redirectTo", null);
        return EnglishFragmentTemplateEngine.create().process("catalog/category-pricing", Set.of("pricingForm"), context);
    }

    /**
     * NEW-1: the production "Monitory" pricing, eleven rows of which ten share two names. Every row keeps its values
     * and its place, and each row of a repeated group says it is one more rule of that group.
     */
    @Test
    void theProductionMonitoryPricingShowsEveryRuleOfARepeatedGroupAsSuch() {
        // given
        CategoryPricingForm form = CategoryPricingForm.from(MonitoryPricingFixture.monitory());

        // when
        String html = rendered(form, form.validate(group -> 0));

        // then
        assertThat(html).doesNotContain("cl-alert is-bad").doesNotContain("??");
        assertThat(occurrences(html, "same group, another rule")).isEqualTo(10);
        assertThat(html).contains("name=\"groups[0].name\"").contains("name=\"groups[10].name\"")
                .doesNotContain("name=\"groups[11].name\"");
        // the first rule after Default is the first stored one, with its parameters as saved
        assertThat(html).containsPattern("name=\"groups\\[1]\\.labelMatch\"\\s+value=\"1440p, Ultrawide, 240\\+ Hz\"")
                .containsPattern("name=\"groups\\[1]\\.multiplier\"\\s+value=\"1,10\"")
                .containsPattern("name=\"groups\\[10]\\.labelMatch\"\\s+value=\"4K, 27&quot;, 144\\+ Hz\"")
                .containsPattern("name=\"groups\\[10]\\.multiplier\"\\s+value=\"1,08\"");
    }

    /** The message of a row whose parameters differ from its group names the group and both rows, at the field and above. */
    @Test
    void aRowWithOtherParametersThanItsGroupSaysWhichRowsDiffer() {
        // given
        CategoryPricingForm form = CategoryPricingForm.from(MonitoryPricingFixture.monitory());
        form.getGroups().get(3).setMultiplier("1,2");

        // when
        String html = rendered(form, form.validate(group -> 0));

        // then
        String message = "Group Ultra Premium has different parameters in row 4 than in row 2 \u2014 the price is taken from the first.";
        assertThat(occurrences(html, message)).isEqualTo(2);
        assertThat(html).contains("href=\"#group-3-multiplier\"").contains("id=\"group-3-multiplier-error\"");
    }

    /** RF-19: the group refused for being in use is back on the form, marked, so the operator sees what is kept. */
    @Test
    void aGroupRefusedForBeingInUseIsShownAgainAndMarked() {
        // given
        CategoryPricingForm form = CategoryPricingForm.from(MonitoryPricingFixture.monitory());
        form.setGroups(new java.util.ArrayList<>(form.getGroups().subList(0, 7)));
        form.setRemovedGroups(List.of("Premium"));
        Map<String, String> errors = form.validate(group -> 2);
        form.restoreGroupInUse(MonitoryPricingFixture.monitory().getPriceDefinitions());

        // when
        String html = rendered(form, errors);

        // then
        assertThat(html).contains("The removed group is used by products");
        assertThat(occurrences(html, ">in use<")).isEqualTo(4);
        assertThat(html).contains("name=\"groups[10].name\"").doesNotContain("??");
    }
}
