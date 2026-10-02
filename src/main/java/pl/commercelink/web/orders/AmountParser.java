package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * An amount of money typed into a text field, read on the server so the browser's language never changes it: "149,99",
 * "149.99", "1 499,99" (spaces, hard spaces and narrow hard spaces between the digits), "-100". Only one decimal mark is
 * allowed, so a thousands separator made of dots ("1.000,50") is not a number rather than a guess, and so are more than
 * two decimal places: "1.000" and "1,000" are most likely a thousand typed with a separator, not one złoty, and a grosz
 * has no third place anyway. A money field is a
 * text field for the same reason: a number field lets the browser read "149,99" by its own locale first (WebKit with
 * en-US turned it into 14999).
 */
public final class AmountParser {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal LIMIT = BigDecimal.valueOf(10_000_000);
    private static final Pattern THIRD_DECIMAL = Pattern.compile("\\.\\d{3}");

    private AmountParser() {
    }

    /**
     * The amount as it will be stored, to the grosz; a blank field is 0 (a cleared fee, the pending payment's amount);
     * null for anything that is not a number of at most two decimal places. An exponent ("1e-3") is still rounded half
     * up to the grosz, so it counts as 0. A value far outside the range is returned unrounded: rounding "1e-999999999"
     * or "1e999999999" to two decimals would build a number of a billion digits, and {@link #inRange} refuses the
     * large one anyway.
     */
    public static BigDecimal parse(String value) {
        if (StringUtils.isBlank(value)) {
            return ZERO;
        }
        String typed = value.replaceAll("[\\s\\u00a0\\u202f]", "").replace(',', '.');
        if (THIRD_DECIMAL.matcher(typed).find()) {
            return null;
        }
        BigDecimal exact;
        try {
            exact = new BigDecimal(typed);
        } catch (NumberFormatException e) {
            return null;
        }
        if (exact.signum() == 0) {
            return ZERO;
        }
        long exponent = (long) exact.precision() - exact.scale() - 1;
        if (exponent < -3) {
            return ZERO;
        }
        if (exponent > 8) {
            return exact;
        }
        return exact.setScale(2, RoundingMode.HALF_UP);
    }

    /** Below 10 000 000 either way, and a finite double once stored. */
    public static boolean inRange(BigDecimal value) {
        return value.abs().compareTo(LIMIT) < 0 && Double.isFinite(value.doubleValue());
    }
}
