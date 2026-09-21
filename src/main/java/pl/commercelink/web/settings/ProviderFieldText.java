package pl.commercelink.web.settings;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.provider.api.ProviderField;

/**
 * Texts of an adapter setting as the settings pages show them. Adapters write labels and placeholders for their own
 * old forms: some mark required fields with a trailing asterisk (the pages say "optional" instead) and some use the
 * placeholder for a mask ("******") or a copy of the label ("Client ID"), which is no example of a value.
 */
public final class ProviderFieldText {

    private ProviderFieldText() {
    }

    public static String label(ProviderField field) {
        return StringUtils.stripEnd(StringUtils.defaultString(field.label()).trim(), " *");
    }

    /** The placeholder as an example value, or null when it is none. */
    public static String example(ProviderField field) {
        String placeholder = StringUtils.trimToNull(field.placeholder());
        if (placeholder == null || field.type() == ProviderField.FieldType.PASSWORD
                || placeholder.chars().allMatch(c -> c == '*' || c == '•')
                || placeholder.equalsIgnoreCase(label(field))) {
            return null;
        }
        return placeholder;
    }
}
