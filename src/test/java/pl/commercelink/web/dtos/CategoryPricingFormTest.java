package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.MonitoryPricingFixture;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.StockDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryPricingFormTest {

    private static CategoryPricingForm valid() {
        CategoryPricingForm form = new CategoryPricingForm();
        form.setCritical("1");
        form.setLow("10");
        form.setHigh("30");
        form.setMinQty("3");
        form.setMinProviders("1");
        CategoryPricingForm.PriceGroupForm def = new CategoryPricingForm.PriceGroupForm();
        def.setName("Default");
        def.setMultiplier("1,05");
        def.setMinProfit("49");
        def.setCritical("0");
        def.setLow("0");
        def.setMedium("0");
        CategoryPricingForm.PriceGroupForm premium = new CategoryPricingForm.PriceGroupForm();
        premium.setName("Premium");
        premium.setMultiplier("1.08");
        premium.setMinProfit("99");
        premium.setCritical("30");
        premium.setLow("20");
        premium.setMedium("10");
        premium.setLabelMatch("RTX 5070");
        premium.setPriceMatch("2 500,00");
        form.setGroups(List.of(def, premium));
        return form;
    }

    @Test
    void validFormMapsToDefinitions() {
        // given
        CategoryPricingForm form = valid();

        // when
        Map<String, String> errors = form.validate(group -> 0);

        // then
        assertThat(errors).isEmpty();
        assertThat(form.toStock().getHighStockThreshold()).isEqualTo(30);
        assertThat(form.toAvailability().getMinNumberOfProviders()).isEqualTo(1);
        List<PriceDefinition> groups = form.toGroups();
        assertThat(groups).extracting(PriceDefinition::getPricingGroup).containsExactly("Default", "Premium");
        assertThat(groups.get(1).getMultiplier()).isEqualTo(1.08);
        assertThat(groups.get(1).getPriceMatch()).isEqualTo(2500.0);
        assertThat(groups.get(1).getLabelMatch()).isEqualTo("RTX 5070");
        assertThat(groups.get(1).isComplete()).isTrue();
    }

    @Test
    void thresholdsMustBeOrdered() {
        // given
        CategoryPricingForm form = valid();
        form.setLow("0");

        // when / then
        assertThat(form.validate(group -> 0)).containsEntry("low", "catalog.category.pricing.low.order");
    }

    @Test
    void defaultGroupIsRequired() {
        // given
        CategoryPricingForm form = valid();
        form.getGroups().get(0).setName("Basic");

        // when / then
        assertThat(form.validate(group -> 0)).containsEntry("groups", "catalog.category.pricing.default.required");
    }

    @Test
    void theSameRuleOfOneGroupTwiceIsAnErrorAndTheMultiplierMustBePositive() {
        // given
        CategoryPricingForm form = valid();
        form.getGroups().get(1).setMultiplier("0");
        form.setGroups(List.of(form.getGroups().get(0), form.getGroups().get(1),
                group(" premium ", "1,08", " rtx 5070 ", "2500")));

        // when
        Map<String, String> errors = form.validate(group -> 0);

        // then
        assertThat(errors).containsEntry("group-2-name", "catalog.category.pricing.group.duplicate")
                .containsEntry("group-1-multiplier", "catalog.category.pricing.multiplier.invalid");
    }

    /**
     * NEW-1: on production one group is several rules ("Ultra Premium" for six label and price pairs). Rows of one
     * name are one group as long as their rules differ; the price is taken from the first row of the name.
     */
    @Test
    void sameGroupWithDifferentLabelRulesIsAccepted() {
        // given
        CategoryPricingForm form = valid();
        form.setGroups(List.of(form.getGroups().get(0), form.getGroups().get(1),
                group("Premium", "1.08", "RTX 5080", "2 500,00"), group("PREMIUM ", "1,08", "RTX 5070", "3000")));

        // when
        Map<String, String> errors = form.validate(group -> 0);

        // then
        assertThat(errors).isEmpty();
        assertThat(form.toGroups()).extracting(PriceDefinition::getPricingGroup)
                .containsExactly("Default", "Premium", "Premium", "Premium");
    }

    /** One group is stored under one spelling: the later rows take the name of the first row of their group. */
    @Test
    void theRowsOfOneGroupAreSavedUnderTheSpellingOfItsFirstRow() {
        // given
        CategoryPricingForm form = valid();
        form.setGroups(List.of(form.getGroups().get(0), form.getGroups().get(1),
                group(" PREMIUM ", "1,08", "RTX 5080", "2500"), group("premium  ", "1,08", "RTX 5090", "3000")));

        // when / then
        assertThat(form.validate(group -> 0)).isEmpty();
        assertThat(form.toGroups()).extracting(PriceDefinition::getPricingGroup)
                .containsExactly("Default", "Premium", "Premium", "Premium");
    }

    @Test
    void theKeyOfAGroupIgnoresCaseAndSpaces() {
        // when / then
        assertThat(CategoryPricingForm.groupKey(" Ultra  Premium ")).isEqualTo(CategoryPricingForm.groupKey("ultra premium"));
        assertThat(CategoryPricingForm.groupKey(null)).isEmpty();
    }

    /** Only the first row of a name prices anything, so a row with other parameters would be silently ignored. */
    @Test
    void sameGroupWithDifferentMultipliersIsRejected() {
        // given
        CategoryPricingForm form = valid();
        form.setGroups(List.of(form.getGroups().get(0), form.getGroups().get(1),
                group("Premium", "1,10", "RTX 5080", "2500")));

        // when
        Map<String, String> errors = form.validate(group -> 0);

        // then
        assertThat(errors).containsOnlyKeys("group-2-multiplier")
                .containsEntry("group-2-multiplier", CategoryPricingForm.PARAMETERS_DIFFER);
        assertThat(form.errorArguments("group-2-multiplier")).containsExactly("Premium", "3", "2");
        assertThat(form.errorArguments("group-1-multiplier")).isEmpty();
    }

    /** The error stands at the first parameter that differs, so the summary link lands on the value to change. */
    @Test
    void theErrorOfDifferentParametersStandsAtTheFirstOneThatDiffers() {
        // given
        CategoryPricingForm form = valid();
        CategoryPricingForm.PriceGroupForm other = group("Premium", "1,080", "RTX 5080", "2500");
        other.setMedium("11");
        other.setLow("21");
        form.setGroups(List.of(form.getGroups().get(0), form.getGroups().get(1), other));

        // when
        Map<String, String> errors = form.validate(group -> 0);

        // then
        assertThat(errors).containsOnlyKeys("group-2-low");
    }

    /** A row whose parameter is mistyped says so at the field; comparing it with its group would only repeat that. */
    @Test
    void aMistypedParameterIsNotComparedWithTheGroup() {
        // given
        CategoryPricingForm form = valid();
        CategoryPricingForm.PriceGroupForm other = group("Premium", "1,08", "RTX 5080", "2500");
        other.setMinProfit("abc");
        form.setGroups(List.of(form.getGroups().get(0), form.getGroups().get(1), other));

        // when / then
        assertThat(form.validate(group -> 0)).containsOnlyKeys("group-2-minProfit")
                .containsEntry("group-2-minProfit", "catalog.category.pricing.amount.invalid");
    }

    /** The eleven definitions of production "Monitory" pass the check and come back as they were, in the same order. */
    @Test
    void theProductionMonitoryPricingSavesUnchanged() {
        // given
        CategoryDefinition monitory = MonitoryPricingFixture.monitory();
        List<PriceDefinition> stored = MonitoryPricingFixture.definitions();

        // when
        CategoryPricingForm form = CategoryPricingForm.from(monitory);
        Map<String, String> errors = form.validate(group -> 0);
        // what CategoryDefinitions.Pricing.applyTo does with the groups of the form
        monitory.setPriceDefinitions(form.toPricing().groups());

        // then
        assertThat(errors).isEmpty();
        assertThat(monitory.getPriceDefinitions()).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(stored);
    }

    /** The rows of a repeated group are marked on the page, all of them, wherever they stand in the list. */
    @Test
    void theRowsOfARepeatedGroupAreMarked() {
        // given
        CategoryPricingForm form = CategoryPricingForm.from(MonitoryPricingFixture.monitory());

        // when / then
        assertThat(form.getGroups()).extracting(form::repeatsAGroup)
                .containsExactly(false, true, true, true, true, true, true, true, true, true, true);
    }

    /** RF-2: a multiplier of three decimals survives a save of the page that does not touch it. */
    @Test
    void aMultiplierOfThreeDecimalsSurvivesASaveThatDoesNotTouchIt() {
        // given
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withPriceDefinition(new PriceDefinition(1.125, 49, 0, 0, 0, "Default"));

        // when
        CategoryPricingForm form = CategoryPricingForm.from(gpu);

        // then
        assertThat(form.getGroups().get(0).getMultiplier()).isEqualTo("1,125");
        assertThat(form.validate(group -> 0)).isEmpty();
        assertThat(form.toGroups().get(0).getMultiplier()).isEqualTo(1.125);
    }

    /** N2: a price threshold of three decimals survives a save of the page that does not touch it, like the multiplier. */
    @Test
    void aPriceThresholdOfThreeDecimalsSurvivesASaveThatDoesNotTouchIt() {
        // given
        PriceDefinition definition = new PriceDefinition(1.0, 49, 0, 0, 0, "Default");
        definition.setLabelMatch("RTX 5070");
        definition.setPriceMatch(1234.567);
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId().withPriceDefinition(definition);

        // when
        CategoryPricingForm form = CategoryPricingForm.from(gpu);

        // then
        assertThat(form.getGroups().get(0).getPriceMatch()).isEqualTo("1234,567");
        assertThat(form.validate(group -> 0)).isEmpty();
        assertThat(form.toGroups().get(0).getPriceMatch()).isEqualTo(1234.567);
    }

    /** N2: a whole-number threshold still displays with two decimals, the way the multiplier field does. */
    @Test
    void aWholeNumberPriceThresholdIsShownWithTwoDecimals() {
        // given
        PriceDefinition definition = new PriceDefinition(1.0, 49, 0, 0, 0, "Default");
        definition.setLabelMatch("RTX 5070");
        definition.setPriceMatch(2500);
        CategoryDefinition gpu = new CategoryDefinition().withName("GPU").withGeneratedId().withPriceDefinition(definition);

        // when
        CategoryPricingForm form = CategoryPricingForm.from(gpu);

        // then
        assertThat(form.getGroups().get(0).getPriceMatch()).isEqualTo("2500,00");
        assertThat(form.toGroups().get(0).getPriceMatch()).isEqualTo(2500.0);
    }

    @Test
    void removingAGroupStillUsedByProductsIsAnError() {
        // given
        CategoryPricingForm form = valid();
        form.setRemovedGroups(List.of("Ultra Premium"));

        // when / then
        assertThat(form.validate(group -> group.equals("Ultra Premium") ? 12 : 0))
                .containsEntry("groups", "catalog.category.pricing.group.inUse");
        assertThat(form.getGroupInUse()).isEqualTo("Ultra Premium");
    }

    /**
     * RF-19: the refused removal puts the group back on the form, every rule of it, marked as in use, so "keep it"
     * is what the page already shows. The rows go to the end: the errors of the other rows keep their numbers.
     */
    @Test
    void aGroupRefusedForBeingInUseComesBackOnTheForm() {
        // given
        CategoryDefinition monitory = MonitoryPricingFixture.monitory();
        CategoryPricingForm form = CategoryPricingForm.from(monitory);
        form.setGroups(new ArrayList<>(form.getGroups().stream().filter(group -> !group.getName().equals("Premium")).toList()));
        form.setRemovedGroups(List.of("Premium"));
        form.validate(group -> group.equals("Premium") ? 3 : 0);

        // when
        form.restoreGroupInUse(monitory.getPriceDefinitions());

        // then
        assertThat(form.getGroups()).hasSize(11);
        assertThat(form.getGroups().subList(7, 11)).allSatisfy(group -> {
            assertThat(group.getName()).isEqualTo("Premium");
            assertThat(group.isInUse()).isTrue();
        });
        assertThat(form.getGroups().subList(7, 11)).extracting(CategoryPricingForm.PriceGroupForm::getLabelMatch)
                .containsExactly("1440p, Ultrawide, 144+ Hz", "4K, 32\", 144+ Hz", "1440p, 27\", 240+ Hz", "4K, 27\", 144+ Hz");
        assertThat(form.getGroups().subList(0, 7)).noneMatch(CategoryPricingForm.PriceGroupForm::isInUse);
    }

    @Test
    void nothingComesBackWhenNoRemovalWasRefused() {
        // given
        CategoryDefinition monitory = MonitoryPricingFixture.monitory();
        CategoryPricingForm form = CategoryPricingForm.from(monitory);
        form.validate(group -> 0);

        // when
        form.restoreGroupInUse(monitory.getPriceDefinitions());

        // then
        assertThat(form.getGroups()).hasSize(11).noneMatch(CategoryPricingForm.PriceGroupForm::isInUse);
    }

    @Test
    void fromPutsDefaultFirstAndFormatsNumbers() {
        // given
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId()
                .withStockDefinition(new StockDefinition(2, 4, 10))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.1, 99, 30, 20, 10, "Premium"))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"));

        // when
        CategoryPricingForm form = CategoryPricingForm.from(category);

        // then
        assertThat(form.getGroups()).extracting(CategoryPricingForm.PriceGroupForm::getName)
                .containsExactly("Default", "Premium");
        assertThat(form.getGroups().get(0).getMultiplier()).isEqualTo("1,05");
        assertThat(form.getGroups().get(1).getMinProfit()).isEqualTo("99");
        assertThat(form.getCritical()).isEqualTo("2");
    }

    /**
     * A product is assigned to the first group whose rules it matches, and the saved order is the matching order
     * (price threshold first, then the name). Listing the groups alphabetically hid which one wins.
     */
    @Test
    void fromKeepsTheGroupsInTheOrderTheyAreMatchedIn() {
        // given
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        category.setPriceDefinitions(List.of(withPriceMatch(new PriceDefinition(1.02, 0, 0, 0, 0, "Alpha"), 0),
                withPriceMatch(new PriceDefinition(1.2, 0, 0, 0, 0, "Zeta"), 5000),
                new PriceDefinition(1.05, 49, 0, 0, 0, "Default")));

        // when
        CategoryPricingForm form = CategoryPricingForm.from(category);

        // then
        assertThat(category.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup)
                .containsExactly("Zeta", "Alpha", "Default");
        assertThat(form.getGroups()).extracting(CategoryPricingForm.PriceGroupForm::getName)
                .containsExactly("Default", "Zeta", "Alpha");
    }

    @Test
    void aGroupSummarisesItsAutomaticAssignmentOnlyWhenItHasOne() {
        // given
        CategoryPricingForm form = valid();

        // when / then
        assertThat(form.getGroups().get(0).autoSummaryKey()).isNull();
        assertThat(form.getGroups().get(1).autoSummaryKey()).isEqualTo("catalog.category.pricing.auto.summary.both");
        form.getGroups().get(1).setPriceMatch("");
        assertThat(form.getGroups().get(1).autoSummaryKey()).isEqualTo("catalog.category.pricing.auto.summary.label");
        form.getGroups().get(1).setLabelMatch(" ");
        form.getGroups().get(1).setPriceMatch("2 500,00");
        assertThat(form.getGroups().get(1).autoSummaryKey()).isEqualTo("catalog.category.pricing.auto.summary.price");
    }

    /**
     * PriceDefinition.matches refuses a blank labelMatch before it ever looks at the price, so a group with only a
     * price threshold assigns nothing. The summary says that instead of reading like a working rule.
     */
    @Test
    void aPriceThresholdWithoutALabelIsReportedAsAssigningNothing() {
        // given
        CategoryPricingForm form = valid();
        CategoryPricingForm.PriceGroupForm priceOnly = form.getGroups().get(1);

        // when / then
        assertThat(priceOnly.isAutoSummaryInactive()).isFalse();
        priceOnly.setLabelMatch("  ");
        assertThat(priceOnly.isAutoSummaryInactive()).isTrue();
        assertThat(priceOnly.autoSummaryKey()).isEqualTo("catalog.category.pricing.auto.summary.price");
        priceOnly.setPriceMatch("");
        assertThat(priceOnly.isAutoSummaryInactive()).isFalse();
        assertThat(priceOnly.autoSummaryKey()).isNull();
    }

    private static CategoryPricingForm.PriceGroupForm group(String name, String multiplier, String label, String priceMatch) {
        CategoryPricingForm.PriceGroupForm group = new CategoryPricingForm.PriceGroupForm();
        group.setName(name);
        group.setMultiplier(multiplier);
        group.setMinProfit("99");
        group.setCritical("30");
        group.setLow("20");
        group.setMedium("10");
        group.setLabelMatch(label);
        group.setPriceMatch(priceMatch);
        return group;
    }

    private static PriceDefinition withPriceMatch(PriceDefinition definition, double priceMatch) {
        definition.setPriceMatch(priceMatch);
        return definition;
    }
}
