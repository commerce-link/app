package pl.commercelink.web.clientreturn;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.RMAReturnForm;
import pl.commercelink.web.dtos.CountryOptions;
import pl.commercelink.web.dtos.FormRules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks the customer's return form before a paid courier is booked with the carrier: nothing invalid may reach it.
 * Values are checked trimmed, so what is validated is what {@link #normalized} later hands on.
 */
public final class ClientReturnFormCheck {

    private static final int MAX_LENGTH = 100;
    private static final String TOO_LONG = "client.return.form.tooLong";
    private static final String PACKAGE_INVALID = "client.return.form.package.invalid";

    private ClientReturnFormCheck() {
    }

    /** Field name to message key, in the order of the form; empty when the form may go to the carrier. */
    public static Map<String, String> validate(RMAReturnForm form, List<ReturnPackageOption> options) {
        ShippingDetails details = normalized(form).getShippingDetails();
        Map<String, String> errors = new LinkedHashMap<>();

        text(errors, "name", details.getName(), "name");
        text(errors, "surname", details.getSurname(), "surname");
        text(errors, "streetAndNumber", details.getStreetAndNumber(), "street");
        if (text(errors, "postalCode", details.getPostalCode(), "postal")
                && !FormRules.isPostalCodeValidFor(details.getPostalCode(), CountryOptions.POLAND)) {
            errors.put("shippingDetails.postalCode", "client.return.form.postal.invalid");
        }
        text(errors, "city", details.getCity(), "city");
        if (text(errors, "email", details.getEmail(), "email") && !FormRules.isEmail(details.getEmail())) {
            errors.put("shippingDetails.email", "client.return.form.email.invalid");
        }
        if (text(errors, "phone", details.getPhone(), "phone") && !FormRules.isPhone(details.getPhone())) {
            errors.put("shippingDetails.phone", "client.return.form.phone.invalid");
        }
        if (details.getCompanyName() != null && details.getCompanyName().length() > MAX_LENGTH) {
            errors.put("shippingDetails.companyName", TOO_LONG);
        }

        String packageId = form == null ? null : form.getSelectedPackageTemplateId();
        if (packageId == null || options.stream().noneMatch(option -> Objects.equals(option.id(), packageId))) {
            errors.put("selectedPackageTemplateId", PACKAGE_INVALID);
        }
        return errors;
    }

    /** A new form with trimmed fields (blank becomes null) and the country forced to Poland; the input stays untouched. */
    public static RMAReturnForm normalized(RMAReturnForm form) {
        RMAReturnForm result = new RMAReturnForm();
        ShippingDetails source = form == null || form.getShippingDetails() == null
                ? new ShippingDetails() : form.getShippingDetails();
        ShippingDetails details = source.copy();
        details.setName(StringUtils.trimToNull(source.getName()));
        details.setSurname(StringUtils.trimToNull(source.getSurname()));
        details.setCompanyName(StringUtils.trimToNull(source.getCompanyName()));
        details.setStreetAndNumber(StringUtils.trimToNull(source.getStreetAndNumber()));
        details.setPostalCode(StringUtils.trimToNull(source.getPostalCode()));
        details.setCity(StringUtils.trimToNull(source.getCity()));
        details.setEmail(StringUtils.trimToNull(source.getEmail()));
        details.setPhone(StringUtils.trimToNull(source.getPhone()));
        details.setCountry(CountryOptions.POLAND);
        result.setShippingDetails(details);
        result.setSelectedPackageTemplateId(form == null ? null : form.getSelectedPackageTemplateId());
        result.setRejectionReason(form == null ? null : form.getRejectionReason());
        return result;
    }

    /** Records a required or too-long error; true when the value is present and fits. */
    private static boolean text(Map<String, String> errors, String field, String value, String keyPart) {
        String key = "shippingDetails." + field;
        if (StringUtils.isBlank(value)) {
            errors.put(key, "client.return.form." + keyPart + ".required");
            return false;
        }
        if (value.length() > MAX_LENGTH) {
            errors.put(key, TOO_LONG);
            return false;
        }
        return true;
    }
}
