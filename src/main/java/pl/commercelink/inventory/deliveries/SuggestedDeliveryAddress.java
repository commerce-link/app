package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.supplier.api.SupplierDeliveryAddress;
import pl.commercelink.orders.ShippingDetails;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SuggestedDeliveryAddress {

    private static final Pattern STREET_PREFIX =
            Pattern.compile("^(ul|ulica|al|aleja|aleje|os|osiedle|pl|plac)\\.?\\s+", Pattern.CASE_INSENSITIVE);

    private SuggestedDeliveryAddress() {
    }

    public static Optional<String> match(ShippingDetails storeDefault, List<SupplierDeliveryAddress> supplierAddresses) {
        if (storeDefault == null || supplierAddresses == null || supplierAddresses.isEmpty()) {
            return Optional.empty();
        }
        String street = normaliseStreet(storeDefault.getStreetAndNumber());
        String postalCode = normalisePostalCode(storeDefault.getPostalCode());
        if (street.isEmpty() || postalCode.isEmpty()) {
            return Optional.empty();
        }

        List<SupplierDeliveryAddress> matches = supplierAddresses.stream()
                .filter(address -> normaliseStreet(address.addressLine()).equals(street)
                        && normalisePostalCode(address.postalCode()).equals(postalCode))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst().id()) : Optional.empty();
    }

    private static String normaliseStreet(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String withoutPrefix = STREET_PREFIX.matcher(value.trim()).replaceFirst("");
        return withoutPrefix.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String normalisePostalCode(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }
}
