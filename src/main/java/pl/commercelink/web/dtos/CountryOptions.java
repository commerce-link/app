package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Countries an address form offers: EU members as ISO codes, Poland first, named in the user's language. */
public final class CountryOptions {

    public static final String POLAND = "PL";

    private static final List<String> COUNTRIES = List.of(
            "PL", "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT",
            "LU", "MT", "NL", "PT", "RO", "SK", "SI", "ES", "SE");

    private CountryOptions() {
    }

    /** A stored value outside the list is kept as the first option, so saving the form does not change it silently. */
    public static List<PickerOption> forPicker(String selected, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        List<PickerOption> others = COUNTRIES.stream()
                .filter(code -> !POLAND.equals(code))
                .map(code -> option(code, locale))
                .sorted(Comparator.comparing(PickerOption::label, collator))
                .toList();

        List<PickerOption> options = new ArrayList<>();
        if (StringUtils.isNotBlank(selected) && !COUNTRIES.contains(selected)) {
            options.add(new PickerOption(selected, selected));
        }
        options.add(option(POLAND, locale));
        options.addAll(others);
        return options;
    }

    public static String displayName(String code, Locale locale) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return COUNTRIES.contains(code) ? Locale.of("", code).getDisplayCountry(locale) : code;
    }

    private static PickerOption option(String code, Locale locale) {
        return new PickerOption(code, Locale.of("", code).getDisplayCountry(locale));
    }
}
