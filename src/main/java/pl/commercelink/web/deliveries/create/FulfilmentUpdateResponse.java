package pl.commercelink.web.deliveries.create;

import pl.commercelink.web.dtos.DeliveryFulfilmentUpdateForm;
import pl.commercelink.web.orders.Money;

/** Answer to the edit-product dialog's fetch: what the row shows now, or why nothing changed. */
public record FulfilmentUpdateResponse(boolean ok, String message, String ean, String mfn, String unitCost) {

    static FulfilmentUpdateResponse saved(DeliveryFulfilmentUpdateForm form, String message) {
        return new FulfilmentUpdateResponse(true, message, form.getEan(), form.getMfn(), Money.input(form.getUnitCost()));
    }

    static FulfilmentUpdateResponse failed(String message) {
        return new FulfilmentUpdateResponse(false, message, null, null, null);
    }
}
