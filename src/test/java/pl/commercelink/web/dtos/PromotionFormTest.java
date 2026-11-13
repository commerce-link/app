package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.Promotion;
import pl.commercelink.discounts.TargetValue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionFormTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 14);

    private static PromotionForm valid() {
        PromotionForm form = PromotionForm.empty();
        form.setName("Tydzień marki ASUS");
        form.setValue("10");
        return form;
    }

    @Test
    void aNewFormIsAPercentagePromotionOnTheWholeBasketWithoutConditionSwitchedOn() {
        // when
        PromotionForm form = PromotionForm.empty();

        // then
        assertThat(form.getConditionTarget()).isEmpty();
        assertThat(form.getConditionQuantity()).isEqualTo("1");
        assertThat(form.getType()).isEqualTo("Percentage");
        assertThat(form.getTarget()).isEmpty();
        assertThat(form.isActive()).isTrue();
    }

    @Test
    void aCompleteFormHasNoErrors() {
        // when / then
        assertThat(valid().validate()).isEmpty();
    }

    @Test
    void nameIsRequired() {
        // given
        PromotionForm form = valid();
        form.setName(" ");

        // when / then
        assertThat(form.validate()).containsEntry("name", "store.promotions.name.required");
    }

    @Test
    void aConditionNeedsItsValuesAndAtLeastOnePiece() {
        // given
        PromotionForm form = valid();
        form.setConditionTarget("Category");
        form.setConditionCategories(List.of(" "));
        form.setConditionQuantity("0");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsEntry("conditionTargetValues", "store.discounts.targetValues.required.Category")
                .containsEntry("conditionQuantity", "store.promotions.conditionQuantity.invalid");
    }

    @Test
    void theQuantityIsAWholeNumber() {
        // given
        PromotionForm fraction = withConditionQuantity("1,5");
        PromotionForm text = withConditionQuantity("dwie");
        PromotionForm two = withConditionQuantity("2");

        // when / then
        assertThat(fraction.validate()).containsEntry("conditionQuantity", "store.promotions.conditionQuantity.invalid");
        assertThat(text.validate()).containsEntry("conditionQuantity", "store.promotions.conditionQuantity.invalid");
        assertThat(two.validate()).isEmpty();
    }

    @Test
    void withoutAConditionTheQuantityIsIgnored() {
        // given
        PromotionForm form = valid();
        form.setConditionQuantity("");

        // when / then
        assertThat(form.validate()).isEmpty();
        assertThat(form.toPromotion("store-1", NOW).getConditionQuantity()).isZero();
    }

    @Test
    void anUnknownConditionIsRejected() {
        // given
        PromotionForm form = valid();
        form.setConditionTarget("Catalog");

        // when / then
        assertThat(form.validate()).containsEntry("conditionTarget", "store.discounts.target.invalid");
    }

    @Test
    void theDiscountFollowsTheRulesOfTheCodes() {
        // given
        PromotionForm form = valid();
        form.setType("Percentage");
        form.setValue("100");
        form.setTarget("Brand");
        form.setMinOrderValue("-1");
        form.setValidFrom("2026-11-30T10:00");
        form.setValidTo("2026-11-29T10:00");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors).containsEntry("value", "store.discounts.value.percentage")
                .containsEntry("targetValues", "store.discounts.targetValues.required.Brand")
                .containsEntry("minOrderValue", "store.discounts.minOrderValue.invalid")
                .containsEntry("validTo", "store.discounts.validTo.beforeFrom");
    }

    @Test
    void errorsComeInTheOrderOfThePage() {
        // given
        PromotionForm form = PromotionForm.empty();
        form.setConditionTarget("Brand");
        form.setConditionQuantity("0");
        form.setMinOrderValue("x");
        form.setTarget("Product");
        form.setValidTo("jutro");

        // when
        Map<String, String> errors = form.validate();

        // then
        assertThat(errors.keySet()).containsExactly("name", "conditionTargetValues", "conditionQuantity",
                "minOrderValue", "value", "targetValues", "validTo");
    }

    @Test
    void toPromotionKeepsTheConditionAndTheDiscountApart() {
        // given
        PromotionForm form = valid();
        form.setName("  Monitor + kabel taniej ");
        form.setConditionTarget("Category");
        form.setConditionCategories(List.of("Monitory", " Monitory ", ""));
        form.setConditionQuantity("2");
        form.setMinOrderValue("499,995");
        form.setType("Amount");
        form.setValue("49,999");
        form.setTarget("Product");
        form.setProducts(List.of(new TargetValue("PIM-1", "Kabel HDMI 2 m")));
        form.setCategories(List.of("Kable i adaptery"));
        form.setValidFrom("2026-11-01T00:00");
        form.setValidTo("2026-12-31T23:59");
        form.setActive(false);

        // when
        Promotion promotion = form.toPromotion("store-1", NOW);

        // then
        assertThat(promotion.getStoreId()).isEqualTo("store-1");
        assertThat(UUID.fromString(promotion.getPromotionId())).isNotNull();
        assertThat(promotion.getName()).isEqualTo("Monitor + kabel taniej");
        assertThat(promotion.getConditionTarget()).isEqualTo(DiscountTarget.Category);
        assertThat(promotion.getConditionValues()).extracting(TargetValue::getId).containsExactly("Monitory");
        assertThat(promotion.getConditionQuantity()).isEqualTo(2);
        assertThat(promotion.getMinOrderValue()).isEqualTo(500.00);
        assertThat(promotion.getType()).isEqualTo(DiscountType.Amount);
        assertThat(promotion.getValue()).isEqualTo(50.00);
        assertThat(promotion.getTarget()).isEqualTo(DiscountTarget.Product);
        assertThat(promotion.getTargetValues()).extracting(TargetValue::getId).containsExactly("PIM-1");
        assertThat(promotion.getValidFrom()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(promotion.getValidTo()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        assertThat(promotion.isActive()).isFalse();
        assertThat(promotion.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void aPromotionWithoutConditionAndOnTheWholeBasketHasNoValues() {
        // given
        PromotionForm form = valid();
        form.setConditionCategories(List.of("Monitory"));
        form.setBrands(List.of("ASUS"));

        // when
        Promotion promotion = form.toPromotion("store-1", NOW);

        // then
        assertThat(promotion.getConditionTarget()).isNull();
        assertThat(promotion.getConditionValues()).isEmpty();
        assertThat(promotion.getTarget()).isNull();
        assertThat(promotion.getTargetValues()).isEmpty();
        assertThat(promotion.getMinOrderValue()).isZero();
    }

    @Test
    void everyPromotionGetsItsOwnId() {
        // when
        Promotion first = valid().toPromotion("store-1", NOW);
        Promotion second = valid().toPromotion("store-1", NOW);

        // then
        assertThat(first.getPromotionId()).isNotEqualTo(second.getPromotionId());
    }

    private static PromotionForm withConditionQuantity(String quantity) {
        PromotionForm form = valid();
        form.setConditionTarget("Brand");
        form.setConditionBrands(List.of("Logitech"));
        form.setConditionQuantity(quantity);
        return form;
    }
}
