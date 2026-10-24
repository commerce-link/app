package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.discounts.DiscountCode;
import pl.commercelink.discounts.DiscountTarget;
import pl.commercelink.discounts.DiscountType;
import pl.commercelink.discounts.TargetValue;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountCodeFormTest {

    private static Store store() {
        Store store = new Store();
        store.setStoreId("store-1");
        store.addPaymentIntegration("paynow");
        return store;
    }

    private static DiscountCodeForm valid() {
        DiscountCodeForm form = DiscountCodeForm.empty();
        form.setCode("lato-10");
        form.setName("Kampania letnia");
        form.setType("Percentage");
        form.setValue("10");
        return form;
    }

    @Test
    void aCompleteFormHasNoErrors() {
        // when
        Map<String, String> errors = valid().validate(store());

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void codeIsRequired() {
        // given
        DiscountCodeForm form = valid();
        form.setCode("  ");

        // when / then
        assertThat(form.validate(store())).containsEntry("code", "store.discounts.code.required");
    }

    @Test
    void codeWithForbiddenCharactersIsRejected() {
        // given
        DiscountCodeForm form = valid();
        form.setCode("lato_10");

        // when / then
        assertThat(form.validate(store())).containsEntry("code", "store.discounts.code.invalid");
    }

    @Test
    void nameIsRequired() {
        // given
        DiscountCodeForm form = valid();
        form.setName(" ");

        // when / then
        assertThat(form.validate(store())).containsEntry("name", "store.discounts.name.required");
    }

    @Test
    void typeMustBeOneOfTheKinds() {
        // given
        DiscountCodeForm form = valid();
        form.setType("Gift");
        DiscountCodeForm withoutValue = valid();
        withoutValue.setType(null);
        withoutValue.setValue("");

        // when / then
        assertThat(form.validate(store())).containsEntry("type", "store.discounts.type.required");
        assertThat(withoutValue.validate(store())).containsEntry("type", "store.discounts.type.required");
    }

    @Test
    void valueIsRequiredAndMustBeANumber() {
        // given
        DiscountCodeForm empty = valid();
        empty.setValue("");
        DiscountCodeForm text = valid();
        text.setValue("dziesięć");

        // when / then
        assertThat(empty.validate(store())).containsEntry("value", "store.discounts.value.required");
        assertThat(text.validate(store())).containsEntry("value", "store.discounts.value.invalid");
    }

    @Test
    void percentageMustBeBetweenOneAndNinetyNine() {
        // when / then
        assertThat(percentage("0").validate(store())).containsEntry("value", "store.discounts.value.percentage");
        assertThat(percentage("100").validate(store())).containsEntry("value", "store.discounts.value.percentage");
        assertThat(percentage("1").validate(store())).doesNotContainKey("value");
        assertThat(percentage("99").validate(store())).doesNotContainKey("value");
    }

    @Test
    void amountMustBeAboveZeroAfterRoundingToGrosz() {
        // when / then
        assertThat(amount("0,004").validate(store())).containsEntry("value", "store.discounts.value.amount");
        assertThat(amount("-5").validate(store())).containsEntry("value", "store.discounts.value.amount");
        assertThat(amount("0,005").validate(store())).doesNotContainKey("value");
    }

    @Test
    void targetMustBeKnown() {
        // given
        DiscountCodeForm form = valid();
        form.setTarget("Catalog");

        // when / then
        assertThat(form.validate(store())).containsEntry("target", "store.discounts.target.invalid");
    }

    @Test
    void categoryCodeNeedsAtLeastOneCategory() {
        // given
        DiscountCodeForm form = valid();
        form.setTarget("Category");
        form.setCategories(List.of(" "));

        // when / then
        assertThat(form.validate(store())).containsEntry("targetValues", "store.discounts.targetValues.required");
    }

    @Test
    void maxUsesMustBeAWholeNumberNotBelowZero() {
        // when / then
        assertThat(maxUses("-1").validate(store())).containsEntry("maxUses", "store.discounts.maxUses.invalid");
        assertThat(maxUses("1,5").validate(store())).containsEntry("maxUses", "store.discounts.maxUses.invalid");
        assertThat(maxUses("0").validate(store())).doesNotContainKey("maxUses");
        assertThat(maxUses("").validate(store())).doesNotContainKey("maxUses");
    }

    @Test
    void minimumOrderValueMustNotBeNegative() {
        // given
        DiscountCodeForm negative = valid();
        negative.setMinOrderValue("-1");
        DiscountCodeForm text = valid();
        text.setMinOrderValue("pięćset");

        // when / then
        assertThat(negative.validate(store())).containsEntry("minOrderValue", "store.discounts.minOrderValue.invalid");
        assertThat(text.validate(store())).containsEntry("minOrderValue", "store.discounts.minOrderValue.invalid");
    }

    @Test
    void unreadableDatesAreRejected() {
        // given
        DiscountCodeForm form = valid();
        form.setValidFrom("jutro");
        form.setValidTo("2026-13-01T10:00");

        // when
        Map<String, String> errors = form.validate(store());

        // then
        assertThat(errors).containsEntry("validFrom", "store.discounts.validFrom.invalid")
                .containsEntry("validTo", "store.discounts.validTo.invalid");
    }

    @Test
    void endBeforeStartIsRejectedButTheSameMomentIsAccepted() {
        // given
        DiscountCodeForm reversed = valid();
        reversed.setValidFrom("2026-11-30T10:00");
        reversed.setValidTo("2026-11-29T10:00");
        DiscountCodeForm same = valid();
        same.setValidFrom("2026-11-30T10:00");
        same.setValidTo("2026-11-30T10:00");

        // when / then
        assertThat(reversed.validate(store())).containsEntry("validTo", "store.discounts.validTo.beforeFrom");
        assertThat(same.validate(store())).isEmpty();
    }

    @Test
    void gatewayMustBeConnectedToTheStore() {
        // given
        DiscountCodeForm foreign = valid();
        foreign.setPaymentProvider("stripe");
        DiscountCodeForm connected = valid();
        connected.setPaymentProvider("paynow");

        // when / then
        assertThat(foreign.validate(store())).containsEntry("paymentProvider", "store.discounts.paymentProvider.invalid");
        assertThat(connected.validate(store())).isEmpty();
    }

    @Test
    void blankOptionalFieldsBecomeNoThresholdNoLimitAnyGatewayAndNoDates() {
        // given
        DiscountCodeForm form = valid();
        form.setMinOrderValue(" ");
        form.setMaxUses("");
        form.setPaymentProvider("");
        form.setValidFrom("");
        form.setValidTo(" ");

        // when
        DiscountCode code = form.toDiscountCode("store-1");

        // then
        assertThat(code.getMinOrderValue()).isZero();
        assertThat(code.getMaxUses()).isZero();
        assertThat(code.getPaymentProvider()).isNull();
        assertThat(code.getValidFrom()).isNull();
        assertThat(code.getValidTo()).isNull();
        assertThat(code.getTarget()).isNull();
        assertThat(code.getTargetValues()).isEmpty();
    }

    @Test
    void toDiscountCodeNormalizesAndRounds() {
        // given
        DiscountCodeForm form = amount("49,999");
        form.setName("  Rabat na podzespoły ");
        form.setTarget("Category");
        form.setCategories(List.of("Karty graficzne", " Procesory ", "Karty graficzne", ""));
        form.setPaymentProvider("paynow");
        form.setMinOrderValue("499,995");
        form.setMaxUses("100");
        form.setValidFrom("2026-11-27T00:00");
        form.setValidTo("2026-11-30T23:59");
        form.setActive(true);

        // when
        DiscountCode code = form.toDiscountCode("store-1");

        // then
        assertThat(code.getStoreId()).isEqualTo("store-1");
        assertThat(code.getCode()).isEqualTo("LATO-10");
        assertThat(code.getName()).isEqualTo("Rabat na podzespoły");
        assertThat(code.getType()).isEqualTo(DiscountType.Amount);
        assertThat(code.getValue()).isEqualTo(50.00);
        assertThat(code.getTarget()).isEqualTo(DiscountTarget.Category);
        assertThat(code.getTargetValues()).extracting(TargetValue::getId).containsExactly("Karty graficzne", "Procesory");
        assertThat(code.getTargetValues()).extracting(TargetValue::getName).containsExactly("Karty graficzne", "Procesory");
        assertThat(code.getPaymentProvider()).isEqualTo("paynow");
        assertThat(code.getMinOrderValue()).isEqualTo(500.00);
        assertThat(code.getMaxUses()).isEqualTo(100);
        assertThat(code.getUsedCount()).isZero();
        assertThat(code.getValidFrom()).isEqualTo(LocalDateTime.of(2026, 11, 27, 0, 0));
        assertThat(code.isActive()).isTrue();
    }

    @Test
    void theLastMinuteOfTheEndCountsInFull() {
        // given
        DiscountCodeForm form = valid();
        form.setValidTo("2026-11-30T23:59");

        // when
        DiscountCode code = form.toDiscountCode("store-1");

        // then
        assertThat(code.getValidTo()).isEqualTo(LocalDateTime.of(2026, 11, 30, 23, 59, 59));
    }

    @Test
    void aNewFormIsAPercentageCodeOnTheWholeBasketSwitchedOn() {
        // when
        DiscountCodeForm form = DiscountCodeForm.empty();

        // then
        assertThat(form.getType()).isEqualTo("Percentage");
        assertThat(form.getTarget()).isEmpty();
        assertThat(form.isActive()).isTrue();
    }

    private static DiscountCodeForm percentage(String value) {
        DiscountCodeForm form = valid();
        form.setType("Percentage");
        form.setValue(value);
        return form;
    }

    private static DiscountCodeForm amount(String value) {
        DiscountCodeForm form = valid();
        form.setType("Amount");
        form.setValue(value);
        return form;
    }

    private static DiscountCodeForm maxUses(String value) {
        DiscountCodeForm form = valid();
        form.setMaxUses(value);
        return form;
    }
}
