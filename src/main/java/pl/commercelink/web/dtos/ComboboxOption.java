package pl.commercelink.web.dtos;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An option of {@code fragments/combobox}: the label on the first line and in the field once chosen, an optional short
 * note in small grey type under it (where it comes from), optionally followed on that line by a starred word that sets
 * the option apart (a suggestion).
 *
 * @param meta    the second line, or null
 * @param starred the word after the star at the end of the second line ("Sugerowana"), or null for no star
 */
public record ComboboxOption(String value, String label, String meta, String starred) {

    public ComboboxOption(String value, String label, String meta) {
        this(value, label, meta, null);
    }

    /** The text of the native select's option, which has no second line and no star: "Fan — Local Catalog (sugerowana)". */
    public String selectText() {
        String text = meta == null ? label : label + " — " + meta;
        return starred == null ? text : text + " (" + spoken(starred) + ")";
    }

    /** What a screen reader says for the option, all of it and not the label alone: "Fan, Local Catalog, sugerowana". */
    public String accessibleName() {
        String name = meta == null ? label : label + ", " + meta;
        return starred == null ? name : name + ", " + spoken(starred);
    }

    // Mid-sentence the word reads lower case; the star's word is capitalised only because it starts its own visual chunk.
    private static String spoken(String word) {
        return word.toLowerCase(Locale.ROOT);
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
