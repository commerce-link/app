package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An address where the store receives goods from suppliers. The name, street, postal code, city and country are required;
 * the email and phone are optional and only checked when given, because nothing reads them: the default address is only
 * shown and matched by street and postal code against the supplier's delivery addresses when a purchase is approved
 * ({@code SuggestedDeliveryAddress}). The company or recipient name is the only name of the address: a saved
 * address drops any separate person name, so the list shows exactly what the form edits.
 */
@Getter
@Setter
public class WarehouseAddressForm {

    private String companyName;
    private String streetAndNumber;
    private String postalCode;
    private String city;
    private String country;
    private String email;
    private String phone;
    private boolean makeDefault;

    public static WarehouseAddressForm empty() {
        WarehouseAddressForm form = new WarehouseAddressForm();
        form.country = CountryOptions.POLAND;
        return form;
    }

    public static WarehouseAddressForm from(ShippingDetails details) {
        WarehouseAddressForm form = new WarehouseAddressForm();
        form.companyName = StringUtils.isNotBlank(details.getCompanyName()) ? details.getCompanyName() : details.getFullName();
        form.streetAndNumber = details.getStreetAndNumber();
        form.postalCode = details.getPostalCode();
        form.city = details.getCity();
        form.country = StringUtils.isNotBlank(details.getCountry()) ? details.getCountry() : CountryOptions.POLAND;
        form.email = details.getEmail();
        form.phone = details.getPhone();
        form.makeDefault = details.is_default();
        return form;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "companyName", companyName, "store.warehouse.address.companyName.required");
        FormRules.requireText(errors, "streetAndNumber", streetAndNumber, "billing.street.required");
        if (FormRules.requireText(errors, "postalCode", postalCode, "billing.postalCode.required")
                && !FormRules.isPostalCodeValidFor(postalCode, country)) {
            errors.put("postalCode", "billing.postalCode.invalid");
        }
        FormRules.requireText(errors, "city", city, "billing.city.required");
        FormRules.requireText(errors, "country", country, "billing.country.required");
        if (StringUtils.isNotBlank(email) && !FormRules.isEmail(email)) {
            errors.put("email", "billing.email.invalid");
        }
        if (StringUtils.isNotBlank(phone) && !FormRules.isPhone(phone)) {
            errors.put("phone", "billing.phone.invalid");
        }
        return errors;
    }

    public ShippingDetails toNewShippingDetails() {
        ShippingDetails details = new ShippingDetails();
        details.set_default(false);
        applyTo(details);
        return details;
    }

    /** Edits the address in place; its id and default flag stay as they were. */
    public void applyTo(ShippingDetails details) {
        details.setName(null);
        details.setSurname(null);
        details.setCompanyName(StringUtils.trimToNull(companyName));
        details.setStreetAndNumber(StringUtils.trimToNull(streetAndNumber));
        details.setPostalCode(StringUtils.trimToNull(postalCode));
        details.setCity(StringUtils.trimToNull(city));
        details.setCountry(StringUtils.trimToNull(country));
        details.setEmail(StringUtils.trimToNull(email));
        details.setPhone(StringUtils.trimToNull(phone));
    }
}
