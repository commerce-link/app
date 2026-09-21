package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMACenter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The address the store ships returned goods back to, per supplier. Unlike a goods-receiving address, this one is
 * handed to the shipping adapter as the receiver ({@code ShippingService.toShipmentAddress}), so the phone is required
 * as well — a courier shipment without a contact number is refused by most carriers. The email stays optional: it only
 * feeds the carrier's notifications. The company name is the address's only name, exactly as before: the form never
 * had a person name, so a shipment carries the company and no full name.
 */
@Getter
@Setter
public class RmaCenterForm {

    private String provider;
    private String companyName;
    private String streetAndNumber;
    private String postalCode;
    private String city;
    private String country;
    private String email;
    private String phone;

    public static RmaCenterForm empty() {
        RmaCenterForm form = new RmaCenterForm();
        form.country = CountryOptions.POLAND;
        return form;
    }

    public static RmaCenterForm from(RMACenter center) {
        RmaCenterForm form = new RmaCenterForm();
        form.provider = center.getProvider();
        ShippingDetails details = center.getShippingDetails();
        if (details == null) {
            form.country = CountryOptions.POLAND;
            return form;
        }
        form.companyName = StringUtils.isNotBlank(details.getCompanyName()) ? details.getCompanyName() : details.getFullName();
        form.streetAndNumber = details.getStreetAndNumber();
        form.postalCode = details.getPostalCode();
        form.city = details.getCity();
        form.country = StringUtils.isNotBlank(details.getCountry()) ? details.getCountry() : CountryOptions.POLAND;
        form.email = details.getEmail();
        form.phone = details.getPhone();
        return form;
    }

    /** Errors in field order, so the summary above the form reads top to bottom like the form itself. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "provider", provider, "rma.center.provider.required");
        FormRules.requireText(errors, "companyName", companyName, "rma.center.companyName.required");
        FormRules.requireText(errors, "streetAndNumber", streetAndNumber, "billing.street.required");
        if (FormRules.requireText(errors, "postalCode", postalCode, "billing.postalCode.required")
                && !FormRules.isPostalCodeValidFor(postalCode, country)) {
            errors.put("postalCode", "billing.postalCode.invalid");
        }
        FormRules.requireText(errors, "city", city, "billing.city.required");
        FormRules.requireText(errors, "country", country, "billing.country.required");
        if (FormRules.requireText(errors, "phone", phone, "rma.center.phone.required")
                && !FormRules.isPhone(phone)) {
            errors.put("phone", "billing.phone.invalid");
        }
        if (StringUtils.isNotBlank(email) && !FormRules.isEmail(email)) {
            errors.put("email", "billing.email.invalid");
        }
        return errors;
    }

    /** Edits the centre in place; its store and id stay as they were, because neither comes from the form. */
    public void applyTo(RMACenter center) {
        center.setProvider(StringUtils.trimToNull(provider));
        ShippingDetails details = center.getShippingDetails();
        if (details == null) {
            details = new ShippingDetails();
            center.setShippingDetails(details);
        }
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
