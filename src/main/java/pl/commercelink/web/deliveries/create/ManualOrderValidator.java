package pl.commercelink.web.deliveries.create;

import org.springframework.validation.BindingResult;
import pl.commercelink.web.dtos.DeliveryCreationForm;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Checks step 2 "Zarejestruj zamówienie" before anything is saved or released. Keys are the field ids of the page
 * (the error summary links to them), in the order the fields stand; values are message keys. A field whose input did
 * not bind (an empty or non-numeric number) is reported at the field instead of answering HTTP 400.
 */
final class ManualOrderValidator {

    private ManualOrderValidator() {
    }

    static Map<String, String> validate(DeliveryCreationForm form, BindingResult binding, boolean requireIdentity) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (requireIdentity && isBlank(form.getExternalDeliveryId())) {
            errors.put("externalDeliveryId", "deliveries.create.error.orderNumber");
        }
        if (binding.hasFieldErrors("estimatedDeliveryAt")) {
            errors.put("estimatedDeliveryAt", "deliveries.create.error.date");
        } else if (requireIdentity && form.getEstimatedDeliveryAt() == null) {
            errors.put("estimatedDeliveryAt", "deliveries.create.error.deliveryDate");
        }
        number(errors, binding, "shippingCost", form.getShippingCost() < 0, "deliveries.create.error.notNegative");
        number(errors, binding, "paymentCost", form.getPaymentCost() < 0, "deliveries.create.error.notNegative");
        number(errors, binding, "tax", form.getTax() < 1, "deliveries.create.error.tax");
        number(errors, binding, "paymentTerms", form.getPaymentTerms() < 0, "deliveries.create.error.notNegative");
        if (!form.hasRequestedItems()) {
            // a dropship delivery (no identity required) has no restock suggestions to mention
            errors.put("items", requireIdentity ? "deliveries.create.error.nothingRequested"
                    : "deliveries.create.error.nothingRequested.dropship");
        }
        return errors;
    }

    private static void number(Map<String, String> errors, BindingResult binding, String field, boolean outOfRange,
                               String rangeKey) {
        if (binding.hasFieldErrors(field)) {
            errors.put(field, "deliveries.create.error.number");
        } else if (outOfRange) {
            errors.put(field, rangeKey);
        }
    }
}
