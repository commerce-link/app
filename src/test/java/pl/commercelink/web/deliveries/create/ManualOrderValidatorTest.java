package pl.commercelink.web.deliveries.create;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManualOrderValidatorTest {

    private static DeliveryCreationForm form(int requestedQty) {
        DeliveryCreationForm form = new DeliveryCreationForm();
        form.setProvider("Acme");
        DeliveryItem item = new DeliveryItem();
        item.setMfn("MFN-1");
        item.setRequestedQty(requestedQty);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private static BindingResult binding(DeliveryCreationForm form) {
        return new BeanPropertyBindingResult(form, "form");
    }

    @Test
    void warehouseNeedsTheSupplierOrderNumberAndDateInPageOrder() {
        // given
        DeliveryCreationForm form = form(1);

        // when
        Map<String, String> errors = ManualOrderValidator.validate(form, binding(form), true);

        // then
        assertThat(errors).containsExactly(
                Map.entry("externalDeliveryId", "deliveries.create.error.orderNumber"),
                Map.entry("estimatedDeliveryAt", "deliveries.create.error.deliveryDate"));
    }

    @Test
    void dropshipAcceptsAnEmptyNumberAndDate() {
        // given
        DeliveryCreationForm form = form(1);

        // when / then
        assertThat(ManualOrderValidator.validate(form, binding(form), false)).isEmpty();
    }

    @Test
    void numberFieldThatDidNotBindIsReportedAtTheField() {
        // given
        DeliveryCreationForm form = form(1);
        form.setExternalDeliveryId("EXT-1");
        form.setEstimatedDeliveryAt(LocalDate.of(2026, 10, 5));
        BindingResult binding = binding(form);
        binding.rejectValue("shippingCost", "typeMismatch");
        binding.rejectValue("paymentTerms", "typeMismatch");

        // when
        Map<String, String> errors = ManualOrderValidator.validate(form, binding, true);

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingCost", "deliveries.create.error.number"),
                Map.entry("paymentTerms", "deliveries.create.error.number"));
    }

    @Test
    void malformedDateIsNotReportedAsMissing() {
        // given
        DeliveryCreationForm form = form(1);
        form.setExternalDeliveryId("EXT-1");
        BindingResult binding = binding(form);
        binding.rejectValue("estimatedDeliveryAt", "typeMismatch");

        // when / then
        assertThat(ManualOrderValidator.validate(form, binding, true))
                .containsExactly(Map.entry("estimatedDeliveryAt", "deliveries.create.error.date"));
    }

    @Test
    void negativeCostsTermsAndATaxBelowOneAreRefused() {
        // given
        DeliveryCreationForm form = form(1);
        form.setShippingCost(-1);
        form.setPaymentCost(-0.01);
        form.setTax(0.23);
        form.setPaymentTerms(-3);

        // when
        Map<String, String> errors = ManualOrderValidator.validate(form, binding(form), false);

        // then
        assertThat(errors).containsExactly(
                Map.entry("shippingCost", "deliveries.create.error.notNegative"),
                Map.entry("paymentCost", "deliveries.create.error.notNegative"),
                Map.entry("tax", "deliveries.create.error.tax"),
                Map.entry("paymentTerms", "deliveries.create.error.notNegative"));
    }

    @Test
    void aDeliveryWithoutAnyRequestedPieceIsRefused() {
        // given
        DeliveryCreationForm form = form(0);

        // when / then
        assertThat(ManualOrderValidator.validate(form, binding(form), true))
                .contains(Map.entry("items", "deliveries.create.error.nothingRequested"));
    }

    @Test
    void anEmptyDropshipDeliveryAsksForALineWithoutMentioningSuggestions() {
        // given
        DeliveryCreationForm form = form(0);

        // when / then
        assertThat(ManualOrderValidator.validate(form, binding(form), false))
                .containsExactly(Map.entry("items", "deliveries.create.error.nothingRequested.dropship"));
    }
}
