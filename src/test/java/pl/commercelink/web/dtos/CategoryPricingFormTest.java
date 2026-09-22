package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.AvailabilityDefinition;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.PriceDefinition;
import pl.commercelink.products.StockDefinition;

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
    void groupNamesAreUniqueAndMultiplierPositive() {
        // given
        CategoryPricingForm form = valid();
        form.getGroups().get(1).setName("default");
        form.getGroups().get(1).setMultiplier("0");

        // when
        Map<String, String> errors = form.validate(group -> 0);

        // then
        assertThat(errors).containsEntry("groups[1].name", "catalog.category.pricing.group.duplicate")
                .containsEntry("groups[1].multiplier", "catalog.category.pricing.multiplier.invalid");
    }

    @Test
    void removingAGroupStillUsedByProductsIsAnError() {
        // given
        CategoryPricingForm form = valid();
        form.setRemovedGroups(List.of("Ultra Premium"));

        // when / then
        assertThat(form.validate(group -> group.equals("Ultra Premium") ? 12 : 0))
                .containsEntry("groups", "catalog.category.pricing.group.inUse");
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
}
