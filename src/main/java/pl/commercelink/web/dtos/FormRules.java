package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
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

    /**
     * The error summary of a form of repeated rows: the text of every field of a row gets the number of the row as the
     * operator counts them (from 1), since one message repeats row after row. A field of no row keeps its text.
     *
     * @param texts    field id to the message shown at the field, in the order of the page
     * @param rowField matches the id of a field of a row; its first group is the index of the row (from 0)
     * @param line     the summary line from the number of the row and the message
     */
    static Map<String, String> numberedSummary(Map<String, String> texts, Pattern rowField,
                                               BiFunction<String, String, String> line) {
        Map<String, String> summary = new LinkedHashMap<>();
        texts.forEach((field, text) -> {
            Matcher row = rowField.matcher(field);
            // a String, not a number: MessageFormat would group the digits of a number by locale ("1 000")
            summary.put(field, row.matches() ? line.apply(String.valueOf(Integer.parseInt(row.group(1)) + 1), text) : text);
        });
        return summary;
    }
}
