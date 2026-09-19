package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/** Currencies a store keeps a bank account in: the złoty first, then those of the neighbouring markets. */
public final class CurrencyOptions {

    public static final String POLISH_ZLOTY = "PLN";
    private static final List<String> CODES = List.of(POLISH_ZLOTY, "EUR", "USD", "GBP", "CHF", "CZK", "SEK", "NOK",
            "DKK", "HUF");

    private CurrencyOptions() {
    }

    public static boolean isOffered(String code) {
        return CODES.contains(code);
    }

    /** A saved code outside the list stays first, so editing the rest of an account does not change its currency. */
    public static List<PickerOption> forPicker(String selected, Locale locale) {
        List<PickerOption> options = new ArrayList<>();
        if (StringUtils.isNotBlank(selected) && !isOffered(selected)) {
            options.add(new PickerOption(selected, selected));
        }
        CODES.forEach(code -> options.add(new PickerOption(code, code + " · " + Currency.getInstance(code).getDisplayName(locale))));
        return options;
    }
}
