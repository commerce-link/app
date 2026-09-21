package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.regex.Pattern;

/** Field checks shared by the settings forms; each form decides which fields are required and in what order. */
final class FormRules {

    private static final Pattern POLISH_POSTAL_CODE = Pattern.compile("\\d{2}-\\d{3}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("[0-9+ ]{9,15}");

    private FormRules() {
    }

    static boolean requireText(Map<String, String> errors, String field, String value, String messageKey) {
        if (StringUtils.isBlank(value)) {
            errors.put(field, messageKey);
            return false;
        }
        return true;
    }

    static boolean isEmail(String value) {
        return EMAIL.matcher(value.trim()).matches();
    }

    static boolean isPhone(String value) {
        return PHONE.matcher(value.trim()).matches();
    }

    /** The XX-XXX format applies to Polish addresses only. */
    static boolean isPostalCodeValidFor(String postalCode, String country) {
        return !CountryOptions.POLAND.equals(StringUtils.trim(country))
                || POLISH_POSTAL_CODE.matcher(postalCode.trim()).matches();
    }
}
