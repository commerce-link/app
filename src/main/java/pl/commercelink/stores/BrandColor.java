package pl.commercelink.stores;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The store's brand colour as used on customer pages. Only hex colours are accepted, because the value is written into
 * style attributes of public pages.
 */
public final class BrandColor {

    private static final Pattern SHORT_HEX = Pattern.compile("#[0-9a-f]{3}");
    private static final Pattern LONG_HEX = Pattern.compile("#[0-9a-f]{6}");

    private BrandColor() {
    }

    /** {@code #rrggbb} in lower case for {@code #RGB} or {@code #RRGGBB}, with or without the hash; empty otherwise. */
    public static Optional<String> normalize(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String color = value.trim().toLowerCase(Locale.ROOT);
        if (!color.startsWith("#")) {
            color = "#" + color;
        }
        if (SHORT_HEX.matcher(color).matches()) {
            color = "#" + color.charAt(1) + color.charAt(1) + color.charAt(2) + color.charAt(2) + color.charAt(3) + color.charAt(3);
        }
        return LONG_HEX.matcher(color).matches() ? Optional.of(color) : Optional.empty();
    }
}
