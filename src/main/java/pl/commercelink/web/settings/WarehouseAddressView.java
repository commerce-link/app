package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.CountryOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An address summarised for a list of addresses: goods-receiving addresses on the warehouse page, pickup addresses on the
 * shipping page (where the email and phone are required, because the courier needs them).
 */
public record WarehouseAddressView(String id, String title, String addressLine, String contactLine, boolean isDefault,
                                   List<String> missingFieldKeys, String editHref, String defaultHref, String deleteHref) {

    public static WarehouseAddressView of(ShippingDetails details, Locale locale, String addressesPath) {
        return of(details, locale, addressesPath, false);
    }

    public static WarehouseAddressView of(ShippingDetails details, Locale locale, String addressesPath, boolean contactRequired) {
        String title = StringUtils.isNotBlank(details.getCompanyName()) ? details.getCompanyName() : details.getFullName();
        String place = join(" ", details.getPostalCode(), details.getCity());
        String addressLine = join(" · ", details.getStreetAndNumber(), place, CountryOptions.displayName(details.getCountry(), locale));
        String contactLine = join(" · ", details.getEmail(), details.getPhone());
        String base = addressesPath + "/" + details.getId();
        return new WarehouseAddressView(details.getId(), title, addressLine, contactLine, details.is_default(),
                missingFieldKeys(details, contactRequired), base, base + "/default", base + "/delete");
    }

    public boolean complete() {
        return missingFieldKeys.isEmpty();
    }

    /** The fields the address form requires; the email and phone only for a pickup address. */
    private static List<String> missingFieldKeys(ShippingDetails details, boolean contactRequired) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(details.getCompanyName()) && StringUtils.isBlank(details.getFullName())) {
            missing.add("store.warehouse.address.missing.name");
        }
        addIfBlank(missing, details.getStreetAndNumber(), "store.warehouse.address.missing.street");
        addIfBlank(missing, details.getPostalCode(), "store.warehouse.address.missing.postalCode");
        addIfBlank(missing, details.getCity(), "store.warehouse.address.missing.city");
        addIfBlank(missing, details.getCountry(), "store.warehouse.address.missing.country");
        if (contactRequired) {
            addIfBlank(missing, details.getEmail(), "store.warehouse.address.missing.email");
            addIfBlank(missing, details.getPhone(), "store.warehouse.address.missing.phone");
        }
        return missing;
    }

    private static void addIfBlank(List<String> missing, String value, String key) {
        if (StringUtils.isBlank(value)) {
            missing.add(key);
        }
    }

    private static String join(String separator, String... parts) {
        String joined = Stream.of(parts).filter(Objects::nonNull).filter(StringUtils::isNotBlank).map(String::trim)
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }
}
