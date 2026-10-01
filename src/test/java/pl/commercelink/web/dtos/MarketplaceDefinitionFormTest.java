package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.products.MarketplaceDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceDefinitionFormTest {

    private static MarketplaceDefinitionForm valid() {
        MarketplaceDefinitionForm form = new MarketplaceDefinitionForm();
        form.setEnabled(true);
        form.setMarkup("1,10");
        form.setMinWarehouseQty("3");
        form.setMinQtyPerDistributor("1");
        form.setMinNumOfDistributors("2");
        form.setMinNumOfLocalDistributors("1");
        form.setMinDistributorsQty("5");
        form.setExportSelectedProducts(true);
        return form;
    }

    @Test
    void validFormMapsToACompleteDefinition() {
        // given
        MarketplaceDefinitionForm form = valid();

        // when
        MarketplaceDefinition definition = form.toDefinition("Allegro");

        // then
        assertThat(form.validate()).isEmpty();
        assertThat(definition.getName()).isEqualTo("Allegro");
        assertThat(definition.getMarkup()).isEqualTo(1.10);
        assertThat(definition.getMinWarehouseQty()).isEqualTo(3);
        assertThat(definition.isExportSelectedProducts()).isTrue();
        assertThat(definition.isComplete()).isTrue();
    }

    @Test
    void markupMustBePositive() {
        // given
        MarketplaceDefinitionForm form = valid();
        form.setMarkup("0");

        // when / then
        assertThat(form.validate()).containsEntry("markup", "catalog.category.marketplace.markup.invalid");
    }

    @Test
    void atLeastOneConditionIsRequiredLikeIsComplete() {
        // given — no warehouse quantity, no distributor threshold: MarketplaceDefinition.isComplete() would be false
        MarketplaceDefinitionForm form = valid();
        form.setMinWarehouseQty("0");
        form.setMinQtyPerDistributor("0");

        // when / then
        assertThat(form.validate()).containsEntry("conditions", "catalog.category.marketplace.conditions.required");
    }

    @Test
    void distributorRuleNeedsAQuantityPerDistributorAndACount() {
        // given
        MarketplaceDefinitionForm form = valid();
        form.setMinWarehouseQty("0");
        form.setMinNumOfDistributors("0");
        form.setMinNumOfLocalDistributors("0");

        // when / then
        assertThat(form.validate()).containsEntry("conditions", "catalog.category.marketplace.conditions.required");
    }

    @Test
    void negativeOrNonNumericThresholdsAreErrors() {
        // given
        MarketplaceDefinitionForm form = valid();
        form.setMinDistributorsQty("-1");
        form.setMinNumOfDistributors("two");

        // when / then
        assertThat(form.validate()).containsEntry("minDistributorsQty", "catalog.category.marketplace.threshold.invalid")
                .containsEntry("minNumOfDistributors", "catalog.category.marketplace.threshold.invalid");
    }

    @Test
    void anUntickedBoxIsAbsentFromThePostSoABoundFormStartsWithBothOff() {
        // when / then
        assertThat(new MarketplaceDefinitionForm().isEnabled()).isFalse();
        assertThat(new MarketplaceDefinitionForm().isExportSelectedProducts()).isFalse();
        assertThat(MarketplaceDefinitionForm.empty().isEnabled()).isTrue();
    }

    @Test
    void fromCopiesTheDefinition() {
        // when
        MarketplaceDefinitionForm form = MarketplaceDefinitionForm.from(new MarketplaceDefinition("Empik", 1.12, 3, 1, 2, 0, 0));

        // then
        assertThat(form.getMarkup()).isEqualTo("1,12");
        assertThat(form.getMinDistributorsQty()).isEqualTo("3");
        assertThat(form.isEnabled()).isTrue();
        assertThat(form.isExportSelectedProducts()).isFalse();
    }

    /** RF-2: saving the page without touching the markup keeps a markup of three decimals as it was. */
    @Test
    void aMarkupOfThreeDecimalsSurvivesASaveThatDoesNotTouchIt() {
        // given
        MarketplaceDefinitionForm form = MarketplaceDefinitionForm.from(new MarketplaceDefinition("Empik", 1.075, 3, 1, 2, 0, 0));

        // when
        MarketplaceDefinition saved = form.toDefinition("Empik");

        // then
        assertThat(form.getMarkup()).isEqualTo("1,075");
        assertThat(form.validate()).isEmpty();
        assertThat(saved.getMarkup()).isEqualTo(1.075);
    }
}
