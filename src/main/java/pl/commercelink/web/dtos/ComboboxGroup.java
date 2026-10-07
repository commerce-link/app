package pl.commercelink.web.dtos;

import java.util.List;
import java.util.Objects;

/**
 * A heading of options in {@code fragments/combobox}: an {@code optgroup} of the native select without JavaScript, a
 * {@code role=group} of the listbox with it.
 */
public record ComboboxGroup(String label, List<Option> options) {

    /** @param meta a short note shown under the label (and in brackets after it in the native select), or null */
    public record Option(String value, String label, String meta) {

        /** The text of the native select's option, which has no second line for the note. */
        public String selectText() {
            return meta == null ? label : label + " (" + meta + ")";
        }
    }

    /** The label of the option holding {@code value}, or null when none does. */
    public static String labelOf(List<ComboboxGroup> groups, String value) {
        return groups.stream()
                .flatMap(group -> group.options().stream())
                .filter(option -> Objects.equals(option.value(), value))
                .map(Option::label)
                .findFirst()
                .orElse(null);
    }
}
