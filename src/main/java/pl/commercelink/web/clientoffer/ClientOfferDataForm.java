package pl.commercelink.web.clientoffer;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.web.dtos.ClientDataDto;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.orders.OrderAddressForm;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The customer's data on the public offer page: the billing details, where the goods go and whether the customer buys
 * as a person or a company. The rules are checked here, on the server, so an error is shown next to its field and the
 * page never has to trust the browser; the form values are trimmed once in {@link #of}. A shipping address is accepted
 * only in Poland, so {@code same} with a foreign billing country is refused and {@code other} is saved with country PL.
 * Personal collection needs no delivery address, so the billing address is copied to it and nothing of shipping is
 * validated.
 */
public record ClientOfferDataForm(ClientDataDto dto, String customerType, String shippingAddress,
                                  boolean personalCollection) {

    public static final String PERSON = "person";
    public static final String COMPANY = "company";
    public static final String SAME = "same";
    public static final String OTHER = "other";

    private static final String KEY = "client.offer.error.";
    private static final Pattern POLISH_POSTAL_CODE = Pattern.compile("\\d{2}-\\d{3}");
    private static final Pattern COUNTRY = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern POLISH_TAX_ID = Pattern.compile("\\d{10}");

    public static ClientOfferDataForm of(ClientDataDto dto, String customerType, String shippingAddress,
                                         boolean personalCollection) {
        ClientDataDto trimmed = new ClientDataDto();
        if (dto != null) {
            trimmed.setOrderReference(dto.getOrderReference());
            trimmed.setShipmentType(dto.getShipmentType());
            trimmed.setPaymentSource(dto.getPaymentSource());
        }
        trimmed.setBillingDetails(trimmed(dto == null ? null : dto.getBillingDetails()));
        trimmed.setShippingDetails(trimmed(dto == null ? null : dto.getShippingDetails()));
        return new ClientOfferDataForm(trimmed,
                COMPANY.equals(customerType) ? COMPANY : PERSON,
                OTHER.equals(shippingAddress) ? OTHER : SAME,
                personalCollection);
    }

    public static ClientOfferDataForm empty(boolean personalCollection) {
        ClientDataDto dto = new ClientDataDto();
        dto.setBillingDetails(BillingDetails._default());
        dto.setShippingDetails(ShippingDetails._default());
        return new ClientOfferDataForm(dto, PERSON, SAME, personalCollection);
    }

    /** The shipping address that mirrors a billing one; the tax id belongs to the invoice only. */
    public static ShippingDetails shippingCopyOf(BillingDetails billing) {
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName(billing.getName());
        shipping.setSurname(billing.getSurname());
        shipping.setCompanyName(billing.getCompanyName());
        shipping.setStreetAndNumber(billing.getStreetAndNumber());
        shipping.setPostalCode(billing.getPostalCode());
        shipping.setCity(billing.getCity());
        shipping.setCountry(billing.getCountry());
        shipping.setEmail(billing.getEmail());
        shipping.setPhone(billing.getPhone());
        return shipping;
    }

    public boolean isCompany() {
        return COMPANY.equals(customerType);
    }

    public boolean isOtherShipping() {
        return OTHER.equals(shippingAddress) && !personalCollection;
    }

    /** Field name to message key, in the order of the form. */
    public Map<String, String> validate() {
        Map<String, String> found = new LinkedHashMap<>();
        BillingDetails billing = dto.getBillingDetails();
        boolean polish = isPolish(billing.getCountry());

        require(found, "billingDetails.name", billing.getName(), "name.required");
        require(found, "billingDetails.surname", billing.getSurname(), "surname.required");
        if (isCompany()) {
            require(found, "billingDetails.companyName", billing.getCompanyName(), "company.required");
            if (require(found, "billingDetails.taxId", billing.getTaxId(), "taxId.required")
                    && polish && !POLISH_TAX_ID.matcher(polishTaxDigits(billing.getTaxId())).matches()) {
                found.put("billingDetails.taxId", KEY + "taxId.invalid");
            }
        }
        require(found, "billingDetails.streetAndNumber", billing.getStreetAndNumber(), "street.required");
        if (require(found, "billingDetails.postalCode", billing.getPostalCode(), "postalCode.required")
                && polish && !POLISH_POSTAL_CODE.matcher(billing.getPostalCode()).matches()) {
            found.put("billingDetails.postalCode", KEY + "postalCode.invalid");
        }
        require(found, "billingDetails.city", billing.getCity(), "city.required");
        if (require(found, "billingDetails.country", billing.getCountry(), "country.required")
                && !COUNTRY.matcher(billing.getCountry()).matches()) {
            found.put("billingDetails.country", KEY + "country.invalid");
        }
        if (require(found, "billingDetails.email", billing.getEmail(), "email.required")
                && !OrderAddressForm.isEmail(billing.getEmail())) {
            found.put("billingDetails.email", KEY + "email.invalid");
        }
        if (require(found, "billingDetails.phone", billing.getPhone(), "phone.required")
                && !OrderAddressForm.isPhone(billing.getPhone())) {
            found.put("billingDetails.phone", KEY + "phone.invalid");
        }

        if (isOtherShipping()) {
            validateShipping(found, dto.getShippingDetails());
        } else if (!personalCollection && COUNTRY.matcher(StringUtils.defaultString(billing.getCountry())).matches()
                && !polish) {
            found.put("shippingAddress", KEY + "shipping.country");
        }
        return found;
    }

    public BillingDetails toBilling() {
        BillingDetails billing = dto.getBillingDetails().copy();
        billing.setCountry(upperCase(billing.getCountry()));
        if (!isCompany()) {
            billing.setCompanyName(null);
            billing.setTaxId(null);
        }
        return billing;
    }

    public ShippingDetails toShipping() {
        if (!isOtherShipping()) {
            return shippingCopyOf(toBilling());
        }
        ShippingDetails shipping = dto.getShippingDetails().copy();
        shipping.setCountry(CountryOptions.POLAND);
        return shipping;
    }

    private static void validateShipping(Map<String, String> found, ShippingDetails shipping) {
        String prefix = "shipping.";
        require(found, "shippingDetails.name", shipping.getName(), prefix + "name.required");
        require(found, "shippingDetails.surname", shipping.getSurname(), prefix + "surname.required");
        require(found, "shippingDetails.streetAndNumber", shipping.getStreetAndNumber(), prefix + "street.required");
        if (require(found, "shippingDetails.postalCode", shipping.getPostalCode(), prefix + "postalCode.required")
                && !POLISH_POSTAL_CODE.matcher(shipping.getPostalCode()).matches()) {
            found.put("shippingDetails.postalCode", KEY + prefix + "postalCode.invalid");
        }
        require(found, "shippingDetails.city", shipping.getCity(), prefix + "city.required");
        if (require(found, "shippingDetails.email", shipping.getEmail(), prefix + "email.required")
                && !OrderAddressForm.isEmail(shipping.getEmail())) {
            found.put("shippingDetails.email", KEY + prefix + "email.invalid");
        }
        if (require(found, "shippingDetails.phone", shipping.getPhone(), prefix + "phone.required")
                && !OrderAddressForm.isPhone(shipping.getPhone())) {
            found.put("shippingDetails.phone", KEY + prefix + "phone.invalid");
        }
    }

    /** True when the value is present; otherwise records the error and returns false. */
    private static boolean require(Map<String, String> found, String field, String value, String keySuffix) {
        if (StringUtils.isBlank(value)) {
            found.put(field, KEY + keySuffix);
            return false;
        }
        return true;
    }

    private static boolean isPolish(String country) {
        return CountryOptions.POLAND.equalsIgnoreCase(country);
    }

    /** Spaces, dashes and a leading "PL" are only decoration of a Polish tax id. */
    private static String polishTaxDigits(String taxId) {
        String compact = taxId.replaceAll("[\\s-]", "");
        return compact.regionMatches(true, 0, "PL", 0, 2) ? compact.substring(2) : compact;
    }

    private static String upperCase(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static BillingDetails trimmed(BillingDetails source) {
        BillingDetails billing = new BillingDetails();
        if (source != null) {
            billing.setName(trimToNull(source.getName()));
            billing.setSurname(trimToNull(source.getSurname()));
            billing.setCompanyName(trimToNull(source.getCompanyName()));
            billing.setTaxId(trimToNull(source.getTaxId()));
            billing.setStreetAndNumber(trimToNull(source.getStreetAndNumber()));
            billing.setPostalCode(trimToNull(source.getPostalCode()));
            billing.setCity(trimToNull(source.getCity()));
            billing.setCountry(trimToNull(source.getCountry()));
            billing.setEmail(trimToNull(source.getEmail()));
            billing.setPhone(trimToNull(source.getPhone()));
        }
        return billing;
    }

    private static ShippingDetails trimmed(ShippingDetails source) {
        ShippingDetails shipping = new ShippingDetails();
        if (source != null) {
            shipping.setName(trimToNull(source.getName()));
            shipping.setSurname(trimToNull(source.getSurname()));
            shipping.setCompanyName(trimToNull(source.getCompanyName()));
            shipping.setStreetAndNumber(trimToNull(source.getStreetAndNumber()));
            shipping.setPostalCode(trimToNull(source.getPostalCode()));
            shipping.setCity(trimToNull(source.getCity()));
            shipping.setCountry(trimToNull(source.getCountry()));
            shipping.setEmail(trimToNull(source.getEmail()));
            shipping.setPhone(trimToNull(source.getPhone()));
        }
        return shipping;
    }
}
