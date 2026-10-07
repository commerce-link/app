package pl.commercelink.web.dtos;

import java.util.List;
import java.util.Objects;

/**
 * An option of {@code fragments/combobox}: the label on the first line and in the field once chosen, an optional short
 * note in small grey type under it (where it comes from, why it fits).
 *
 * @param meta the second line, or null
 */
public record ComboboxOption(String value, String label, String meta) {

    /** The text of the native select's option, which has no second line. */
    public String selectText() {
        return meta == null ? label : label + " — " + meta;
    }

    /** What a screen reader says for the option: both lines, not the label alone. */
    public String accessibleName() {
        return meta == null ? label : label + ", " + meta;
    }

    /** The label of the option holding {@code value}, or null when none does. */
    public static String labelOf(List<ComboboxOption> options, String value) {
        return options.stream()
                .filter(option -> Objects.equals(option.value(), value))
                .map(ComboboxOption::label)
                .findFirst()
                .orElse(null);
    }
}
