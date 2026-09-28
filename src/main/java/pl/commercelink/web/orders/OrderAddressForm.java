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
 * {@code shippingDetails.city}), which are also their ids. The rules are the ones the old address page enforced in
 * the browser (required fields, a two-letter country code), checked on the server so an error can be shown next to its
 * field; the phone counts digits (9-15) instead of the old page's pattern, which refused real numbers written with
 * separators ("+48 22 390 45 10"). The company and the tax id stay optional; values are stored as typed.
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

    /** Field name to message key, in the order of the form. */
    public Map<String, String> validate() {
        Map<String, String> found = new LinkedHashMap<>();
        require(found, "name", name, "billing.name.required");
        require(found, "surname", surname, "billing.surname.required");
        require(found, "streetAndNumber", streetAndNumber, "billing.street.required");
        require(found, "postalCode", postalCode, "billing.postalCode.required");
        require(found, "city", city, "billing.city.required");
        if (require(found, "country", country, "billing.country.required") && !COUNTRY.matcher(country).matches()) {
            found.put(field("country"), "order.address.country.invalid");
        }
        if (require(found, "email", email, "billing.email.required") && !EMAIL.matcher(email.trim()).matches()) {
            found.put(field("email"), "billing.email.invalid");
        }
        if (require(found, "phone", phone, "billing.phone.required") && !isPhone(phone)) {
            found.put(field("phone"), "billing.phone.invalid");
        }
        return found;
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
