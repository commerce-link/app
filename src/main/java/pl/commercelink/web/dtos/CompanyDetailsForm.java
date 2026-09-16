package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.BillingDetails;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The store's own company details. A store always sells as a company, so the company name and tax ID are required;
 * the person-name branch of {@link BillingDetails#isProperlyFilled()} is meant for customers and is not offered here.
 */
@Getter
@Setter
public class CompanyDetailsForm {

    private static final String POLAND = "PL";
    private static final List<String> COUNTRIES = List.of(
            "PL", "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT",
            "LU", "MT", "NL", "PT", "RO", "SK", "SI", "ES", "SE");
    private static final Pattern POLISH_POSTAL_CODE = Pattern.compile("\\d{2}-\\d{3}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("[0-9+ ]{9,15}");

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
            form.country = POLAND;
        }
        return form;
    }

    /** Field name to message key, in the order the fields appear on the page. Empty when the details can be saved. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        requireText(errors, "companyName", companyName, "billing.companyName.required");
        requireText(errors, "taxId", taxId, "billing.taxId.required");
        requireText(errors, "streetAndNumber", streetAndNumber, "billing.street.required");
        if (requireText(errors, "postalCode", postalCode, "billing.postalCode.required")
                && POLAND.equals(StringUtils.trim(country))
                && !POLISH_POSTAL_CODE.matcher(postalCode.trim()).matches()) {
            errors.put("postalCode", "billing.postalCode.invalid");
        }
        requireText(errors, "city", city, "billing.city.required");
        requireText(errors, "country", country, "billing.country.required");
        if (requireText(errors, "email", email, "billing.email.required") && !EMAIL.matcher(email.trim()).matches()) {
            errors.put("email", "billing.email.invalid");
        }
        if (StringUtils.isNotBlank(phone) && !PHONE.matcher(phone.trim()).matches()) {
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

    /** EU countries named in the user's language, Poland first; a stored value outside the list is kept as the first option. */
    public static List<PickerOption> countryOptions(String selected, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        List<PickerOption> others = COUNTRIES.stream()
                .filter(code -> !POLAND.equals(code))
                .map(code -> countryOption(code, locale))
                .sorted(Comparator.comparing(PickerOption::label, collator))
                .toList();

        List<PickerOption> options = new ArrayList<>();
        if (StringUtils.isNotBlank(selected) && !COUNTRIES.contains(selected)) {
            options.add(new PickerOption(selected, selected));
        }
        options.add(countryOption(POLAND, locale));
        options.addAll(others);
        return options;
    }

    private static PickerOption countryOption(String code, Locale locale) {
        return new PickerOption(code, Locale.of("", code).getDisplayCountry(locale));
    }

    private static boolean requireText(Map<String, String> errors, String field, String value, String messageKey) {
        if (StringUtils.isBlank(value)) {
            errors.put(field, messageKey);
            return false;
        }
        return true;
    }
}
