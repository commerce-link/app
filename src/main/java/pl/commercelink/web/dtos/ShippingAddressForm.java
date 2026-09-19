package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An address shipments leave from (the courier picks the parcels up there) or the sender printed on the label. Unlike a
 * goods-receiving address, the email and phone are required: the courier provider rejects a shipment without them.
 * The contact person is optional, but some carriers (GLS in the Furgonetka sandbox) refuse a sender without one; it is
 * stored as the address's name, so {@code ShippingService} sends it as the person next to the company name.
 */
@Getter
@Setter
public class ShippingAddressForm {

    private String companyName;
    private String contactPerson;
    private String streetAndNumber;
    private String postalCode;
    private String city;
    private String country;
    private String email;
    private String phone;
    private boolean makeDefault;

    public static ShippingAddressForm empty() {
        ShippingAddressForm form = new ShippingAddressForm();
        form.country = CountryOptions.POLAND;
        return form;
    }

    public static ShippingAddressForm from(ShippingDetails details) {
        ShippingAddressForm form = new ShippingAddressForm();
        String fullName = StringUtils.trimToNull(details.getFullName());
        // An old address had only a person's name: it becomes the name of the address, not a contact next to nothing.
        form.companyName = StringUtils.isNotBlank(details.getCompanyName()) ? details.getCompanyName() : fullName;
        form.contactPerson = StringUtils.isNotBlank(details.getCompanyName()) ? fullName : null;
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
        FormRules.requireText(errors, "companyName", companyName, "store.shipping.address.companyName.required");
        FormRules.requireText(errors, "streetAndNumber", streetAndNumber, "billing.street.required");
        if (FormRules.requireText(errors, "postalCode", postalCode, "billing.postalCode.required")
                && !FormRules.isPostalCodeValidFor(postalCode, country)) {
            errors.put("postalCode", "billing.postalCode.invalid");
        }
        FormRules.requireText(errors, "city", city, "billing.city.required");
        FormRules.requireText(errors, "country", country, "billing.country.required");
        if (FormRules.requireText(errors, "email", email, "billing.email.required") && !FormRules.isEmail(email)) {
            errors.put("email", "billing.email.invalid");
        }
        if (FormRules.requireText(errors, "phone", phone, "billing.phone.required") && !FormRules.isPhone(phone)) {
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
        details.setName(StringUtils.trimToNull(contactPerson));
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
