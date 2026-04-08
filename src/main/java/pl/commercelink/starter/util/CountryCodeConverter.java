package pl.commercelink.starter.util;

import java.util.Locale;

public class CountryCodeConverter {

    public static String getCountryCode(String countryName) {

        Locale[] searchLocales = {
                new Locale("pl", "PL"),
                Locale.ENGLISH,
        };

        for (Locale locale : Locale.getAvailableLocales()) {
            for (Locale searchLocale : searchLocales) {
                if (locale.getDisplayCountry(searchLocale)
                        .equalsIgnoreCase(countryName)) {
                    return locale.getCountry();
                }
            }
        }

        return countryName;
    }
}