package pl.commercelink.web.dtos;

import pl.commercelink.inventory.deliveries.SupplierPurchaseService.DeliveryAddressChoices;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * View model for the delivery-address picker. Suppliers with an optional literal address get a
 * leading empty-value option, so the operator can always fall back to the supplier account address.
 */
public record DeliveryAddressPicker(boolean required, List<SupplierDeliveryAddress> addresses,
                                    List<PickerOption> options) {

    public static DeliveryAddressPicker of(DeliveryAddressChoices choices, String accountAddressLabel) {
        List<PickerOption> options = new ArrayList<>();
        if (!choices.required() && !choices.options().isEmpty()) {
            options.add(new PickerOption("", accountAddressLabel));
        }
        choices.options().forEach(address -> options.add(new PickerOption(address.id(), address.label())));
        return new DeliveryAddressPicker(choices.required(), choices.options(), List.copyOf(options));
    }
}
