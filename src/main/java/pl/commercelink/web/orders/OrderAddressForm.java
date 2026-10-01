package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The billing or the shipping address of an order as its edit form shows it: in the dialog of the customer card and
 * on the address page without JavaScript. Fields post under the names the order binds ({@code billingDetails.city},
 * {@code shippingDetails.city}), which are also their ids. The rules come from the old address page, which enforced
 * them only in the browser; they are checked on the server so an error can be shown next to its field, and only on
 * the fields the operator changed (see {@link #validate(OrderAddressForm)}). The phone counts digits (9-15) instead
 * of the old page's pattern, which refused real numbers written with separators ("+48 22 390 45 10"). The company and
 * the tax id stay optional; values are stored as typed, except a newly typed country code, stored in capitals.
 */
public record OrderAddressForm(String orderId, String type, String name, String surname, String companyName,
                               String taxId, String streetAndNumber, String postalCode, String city, String country,
                               String email, String phone, Map<String, String> errors, String refusal) {

    public static final String BILLING = "billing";
    public static final String SHIPPING = "shipping";

    private static final Pattern COUNTRY = Pattern.compile("[A-Z]{2}");
    /** An optional leading "+", then digits with spaces, dashes, dots or parentheses between them. */
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9 ().-]+");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public static boolean isType(String type) {
        return BILLING.equals(type) || SHIPPING.equals(type);
    }

    public static OrderAddressForm billing(String orderId, BillingDetails details) {
        BillingDetails d = details != null ? details : new BillingDetails();
        return new OrderAddressForm(orderId, BILLING, d.getName(), d.getSurname(), d.getCompanyName(), d.getTaxId(),
                d.getStreetAndNumber(), d.getPostalCode(), d.getCity(), d.getCountry(), d.getEmail(), d.getPhone(),
                Map.of(), null);
    }

    public static OrderAddressForm shipping(String orderId, ShippingDetails details) {
        ShippingDetails d = details != null ? details : new ShippingDetails();
        return new OrderAddressForm(orderId, SHIPPING, d.getName(), d.getSurname(), d.getCompanyName(), null,
                d.getStreetAndNumber(), d.getPostalCode(), d.getCity(), d.getCountry(), d.getEmail(), d.getPhone(),
                Map.of(), null);
    }

    public boolean isBilling() {
        return BILLING.equals(type);
    }

    /** The posted name of a field, also its id: billingDetails.city. */
    public String field(String property) {
        return (isBilling() ? "billingDetails." : "shippingDetails.") + property;
    }

    /** The message key of the field's error, or null. */
    public String error(String property) {
        return errors.get(field(property));
    }

    /** Every field checked, as for an address that has nothing saved yet. */
    public Map<String, String> validate() {
        return validate(null);
    }

    /**
     * Field name to message key, in the order of the form. Only a field whose value differs from the saved one (after
     * trimming; the country in any case) is checked: marketplace imports store values these rules would refuse (a
     * company billing without a person, a country name, no phone), and an operator correcting the street must not be
     * made to invent them. A person's name is needed on a billing address only without a company; the surname is
     * always optional (a single-word name is stored with an empty one).
     */
    public Map<String, String> validate(OrderAddressForm saved) {
        Map<String, String> found = new LinkedHashMap<>();
        if (isBilling()) {
            boolean nameOrCompanyChanged = changed(name, saved == null ? null : saved.name, saved == null)
                    || changed(companyName, saved == null ? null : saved.companyName, saved == null);
            if (nameOrCompanyChanged && StringUtils.isBlank(name) && StringUtils.isBlank(companyName)) {
                found.put(field("name"), "order.address.name.or.company.required");
            }
        } else if (changed(name, saved == null ? null : saved.name, saved == null)) {
            require(found, "name", name, "billing.name.required");
        }
        if (changed(streetAndNumber, saved == null ? null : saved.streetAndNumber, saved == null)) {
            require(found, "streetAndNumber", streetAndNumber, "billing.street.required");
        }
        if (changed(postalCode, saved == null ? null : saved.postalCode, saved == null)) {
            require(found, "postalCode", postalCode, "billing.postalCode.required");
        }
        if (changed(city, saved == null ? null : saved.city, saved == null)) {
            require(found, "city", city, "billing.city.required");
        }
        if (countryChanged(saved) && require(found, "country", country, "billing.country.required")
                && !COUNTRY.matcher(normalizedCountry(country)).matches()) {
            found.put(field("country"), "order.address.country.invalid");
        }
        if (changed(email, saved == null ? null : saved.email, saved == null)
                && require(found, "email", email, "billing.email.required") && !EMAIL.matcher(email.trim()).matches()) {
            found.put(field("email"), "billing.email.invalid");
        }
        if (changed(phone, saved == null ? null : saved.phone, saved == null)
                && require(found, "phone", phone, "billing.phone.required") && !isPhone(phone)) {
            found.put(field("phone"), "billing.phone.invalid");
        }
        return found;
    }

    /** The country to store: a newly typed code trimmed and in capitals, an unchanged saved value as it was. */
    public String countryToStore(OrderAddressForm saved) {
        return countryChanged(saved) ? normalizedCountry(country) : country;
    }

    private boolean countryChanged(OrderAddressForm saved) {
        return saved == null || !normalizedCountry(country).equals(normalizedCountry(saved.country));
    }

    private static String normalizedCountry(String value) {
        return StringUtils.trimToEmpty(value).toUpperCase(java.util.Locale.ROOT);
    }

    /** With nothing saved to compare with (validate()), every field counts as changed. */
    private static boolean changed(String posted, String saved, boolean nothingSaved) {
        return nothingSaved || !StringUtils.trimToEmpty(posted).equals(StringUtils.trimToEmpty(saved));
    }

    /** "+48 22 390 45 10", "(22) 390-45-10", "600700800": allowed characters only, 9 to 15 digits. */
    public static boolean isPhone(String value) {
        if (!PHONE.matcher(value.trim()).matches()) {
            return false;
        }
        long digits = value.chars().filter(Character::isDigit).count();
        return digits >= 9 && digits <= 15;
    }

    public OrderAddressForm withErrors(Map<String, String> found) {
        return new OrderAddressForm(orderId, type, name, surname, companyName, taxId, streetAndNumber, postalCode, city,
                country, email, phone, found, refusal);
    }

    /** A reason the whole form was refused (a closed or locked order), already translated. */
    public OrderAddressForm withRefusal(String text) {
        return new OrderAddressForm(orderId, type, name, surname, companyName, taxId, streetAndNumber, postalCode, city,
                country, email, phone, errors, text);
    }

    private boolean require(Map<String, String> found, String property, String value, String key) {
        if (StringUtils.isBlank(value)) {
            found.put(field(property), key);
            return false;
        }
        return true;
    }
}
