package pl.commercelink.stores;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * International bank account numbers as the store enters them and as customers read them. Stored compact (no spaces,
 * upper case); shown in groups of four, the way banks print them, so a number can be compared against a statement.
 */
public final class Iban {

    private static final Pattern SHAPE = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");
    // A Polish account number is often copied without the country code (NRB, 26 digits).
    private static final Pattern POLISH_ACCOUNT_NUMBER = Pattern.compile("\\d{26}");

    private Iban() {
    }

    /** The number without spaces or dashes, upper case, with "PL" added to a bare 26-digit Polish number; null when blank. */
    public static String normalize(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return POLISH_ACCOUNT_NUMBER.matcher(compact).matches() ? "PL" + compact : compact;
    }

    /** Shape and the ISO 13616 check digits (mod 97) of a normalized number. */
    public static boolean isValid(String normalized) {
        if (normalized == null || !SHAPE.matcher(normalized).matches()) {
            return false;
        }
        String rearranged = normalized.substring(4) + normalized.substring(0, 4);
        int remainder = 0;
        for (char c : rearranged.toCharArray()) {
            int value = Character.isDigit(c) ? c - '0' : c - 'A' + 10;
            remainder = (value > 9 ? remainder * 100 + value : remainder * 10 + value) % 97;
        }
        return remainder == 1;
    }

    public static String countryCode(String normalized) {
        return normalized == null || normalized.length() < 2 ? null : normalized.substring(0, 2);
    }

    /** Groups of four for reading; the value is shown as stored when it is not a number at all. */
    public static String grouped(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < normalized.length(); i += 4) {
            if (i > 0) {
                grouped.append(' ');
            }
            grouped.append(normalized, i, Math.min(i + 4, normalized.length()));
        }
        return grouped.toString();
    }
}
