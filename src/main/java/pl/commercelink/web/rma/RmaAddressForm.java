package pl.commercelink.web.rma;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.orders.OrderAddressForm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The address an RMA ships the goods back to (RMA.shippingDetails), as its edit page shows it. Every field
 * "Ship to client" needs (ShippingDetails#isProperlyFilled) is required whether changed or not: an address without
 * them would be saved only to be refused when the parcel is booked. Formats (country code, email, phone) are checked
 * only on a field the operator changed, as on the order's address form, so a marketplace address with a country name
 * does not block correcting the street.
 */
public record RmaAddressForm(String name, String surname, String companyName, String streetAndNumber,
                             String postalCode, String city, String country, String email, String phone,
                             Map<String, String> errors) {

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");

    public static RmaAddressForm of(ShippingDetails details) {
        ShippingDetails d = details != null ? details : new ShippingDetails();
        return new RmaAddressForm(d.getName(), d.getSurname(), d.getCompanyName(), d.getStreetAndNumber(),
                d.getPostalCode(), d.getCity(), d.getCountry(), d.getEmail(), d.getPhone(), Map.of());
    }

    /** The posted name of a field, also its id. */
    public static String field(String property) {
        return "shippingDetails." + property;
    }

    public String error(String property) {
        return errors.get(field(property));
    }

    public RmaAddressForm withErrors(Map<String, String> found) {
        return new RmaAddressForm(name, surname, companyName, streetAndNumber, postalCode, city, country, email, phone,
                found);
    }

    /** Field name to message key, in the order of the form; saved null checks every format. */
    public Map<String, String> validate(ShippingDetails saved) {
        ShippingDetails s = saved != null ? saved : new ShippingDetails();
        boolean nothingSaved = saved == null;
        Map<String, String> found = new LinkedHashMap<>();
        if (StringUtils.isBlank(name) && StringUtils.isBlank(surname) && StringUtils.isBlank(companyName)) {
            found.put(field("name"), "order.address.name.or.company.required");
        }
        require(found, "streetAndNumber", streetAndNumber, "billing.street.required");
        require(found, "postalCode", postalCode, "billing.postalCode.required");
        require(found, "city", city, "billing.city.required");
        if (require(found, "country", country, "billing.country.required") && countryChanged(s, nothingSaved)
                && !COUNTRY.matcher(normalizedCountry(country)).matches()) {
            found.put(field("country"), "order.address.country.invalid");
        }
        if (require(found, "email", email, "billing.email.required") && changed(email, s.getEmail(), nothingSaved)
                && !OrderAddressForm.isEmail(email)) {
            found.put(field("email"), "billing.email.invalid");
        }
        if (require(found, "phone", phone, "billing.phone.required") && changed(phone, s.getPhone(), nothingSaved)
                && !OrderAddressForm.isPhone(phone)) {
            found.put(field("phone"), "billing.phone.invalid");
        }
        return found;
    }

    /** The address to store: the saved one's id and default flag kept, values trimmed, a newly typed country in capitals. */
    public ShippingDetails applyTo(ShippingDetails existing) {
        ShippingDetails base = existing != null ? existing : new ShippingDetails();
        ShippingDetails updated = base.withEditableFieldsFrom(toDetails());
        updated.set_default(base.is_default());
        updated.setCountry(countryChanged(base, existing == null) ? normalizedCountry(country) : base.getCountry());
        return updated;
    }

    /** Whether any field differs from the given address after trimming (null differs from everything). */
    public boolean differsFrom(ShippingDetails other) {
        if (other == null) {
            return true;
        }
        RmaAddressForm o = of(other);
        return Stream.of(new String[][]{{name, o.name}, {surname, o.surname}, {companyName, o.companyName},
                        {streetAndNumber, o.streetAndNumber}, {postalCode, o.postalCode}, {city, o.city},
                        {country, o.country}, {email, o.email}, {phone, o.phone}})
                .anyMatch(pair -> !Objects.equals(StringUtils.trimToEmpty(pair[0]), StringUtils.trimToEmpty(pair[1])));
    }

    /** Whether an address has anything an operator typed; the country alone is the default of a new address. */
    public static boolean hasAnyValue(ShippingDetails details) {
        return details != null && Stream.of(details.getName(), details.getSurname(), details.getCompanyName(),
                        details.getStreetAndNumber(), details.getPostalCode(), details.getCity(), details.getEmail(),
                        details.getPhone())
                .anyMatch(StringUtils::isNotBlank);
    }

    private ShippingDetails toDetails() {
        ShippingDetails d = new ShippingDetails();
        d.setName(name);
        d.setSurname(surname);
        d.setCompanyName(companyName);
        d.setStreetAndNumber(streetAndNumber);
        d.setPostalCode(postalCode);
        d.setCity(city);
        d.setEmail(email);
        d.setPhone(phone);
        return d;
    }

    private boolean countryChanged(ShippingDetails saved, boolean nothingSaved) {
        return nothingSaved || !normalizedCountry(country).equals(normalizedCountry(saved.getCountry()));
    }

    private static boolean changed(String posted, String saved, boolean nothingSaved) {
        return nothingSaved || !StringUtils.trimToEmpty(posted).equals(StringUtils.trimToEmpty(saved));
    }

    private static String normalizedCountry(String value) {
        return StringUtils.trimToEmpty(value).toUpperCase(Locale.ROOT);
    }

    private static boolean require(Map<String, String> found, String property, String value, String key) {
        if (StringUtils.isBlank(value)) {
            found.put(field(property), key);
            return false;
        }
        return true;
    }
}
