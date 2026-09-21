package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The store's own company details. A store always sells as a company, so the company name and tax ID are required;
 * the person-name branch of {@link BillingDetails#isProperlyFilled()} is meant for customers and is not offered here.
 */
@Getter
@Setter
public class CompanyDetailsForm {

    private String companyName;
    private String taxId;
    private String streetAndNumber;
    private String postalCode;
    private String city;
    private String country;
    private String email;
    private String phone;

    public static CompanyDetailsForm from(BillingDetails details) {
        CompanyDetailsForm form = new CompanyDetailsForm();
        if (details != null) {
            form.companyName = details.getCompanyName();
            form.taxId = details.getTaxId();
            form.streetAndNumber = details.getStreetAndNumber();
            form.postalCode = details.getPostalCode();
            form.city = details.getCity();
            form.country = details.getCountry();
            form.email = details.getEmail();
            form.phone = details.getPhone();
        }
        if (StringUtils.isBlank(form.country)) {
            form.country = CountryOptions.POLAND;
        }
        return form;
    }

    /** Field name to message key, in the order the fields appear on the page. Empty when the details can be saved. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        FormRules.requireText(errors, "companyName", companyName, "billing.companyName.required");
        FormRules.requireText(errors, "taxId", taxId, "billing.taxId.required");
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
        if (StringUtils.isNotBlank(phone) && !FormRules.isPhone(phone)) {
            errors.put("phone", "billing.phone.invalid");
        }
        return errors;
    }

    /** A copy of the existing details with the edited fields replaced; the existing object is left untouched. */
    public BillingDetails applyTo(BillingDetails existing) {
        BillingDetails details = existing != null ? existing.copy() : new BillingDetails();
        details.setCompanyName(StringUtils.trimToNull(companyName));
        details.setTaxId(StringUtils.trimToNull(taxId));
        details.setStreetAndNumber(StringUtils.trimToNull(streetAndNumber));
        details.setPostalCode(StringUtils.trimToNull(postalCode));
        details.setCity(StringUtils.trimToNull(city));
        details.setCountry(StringUtils.trimToNull(country));
        details.setEmail(StringUtils.trimToNull(email));
        details.setPhone(StringUtils.trimToNull(phone));
        return details;
    }
}
