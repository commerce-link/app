package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

/**
 * Numbers typed into text fields. Forms take numbers as text so an empty or mistyped value ends up as a message under
 * the field instead of a binding error page; the decimal separator may be a comma (Polish keyboard) or a dot.
 */
public final class FormNumbers {

    private static final DecimalFormatSymbols POLISH = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pl-PL"));

    static {
        POLISH.setGroupingSeparator(' ');
        POLISH.setDecimalSeparator(',');
    }

    private FormNumbers() {
    }

    public static Optional<Integer> integer(String value) {
        String clean = clean(value);
        if (clean.isEmpty() || !clean.matches("-?\\d+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(clean));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<Double> decimal(String value) {
        String clean = clean(value).replace(',', '.');
        if (clean.isEmpty() || !clean.matches("-?\\d+(\\.\\d+)?")) {
            return Optional.empty();
        }
        return Optional.of(Double.parseDouble(clean));
    }

    public static String format(double value) {
        return new DecimalFormat("#,##0.00", POLISH).format(value);
    }

    public static String formatInt(long value) {
        return new DecimalFormat("#,##0", POLISH).format(value);
    }

    /** Multipliers are shown with two decimals ("1,10"), integers as they are ("49"). */
    public static String formatDecimalField(double value) {
        return new DecimalFormat("0.00", POLISH).format(value);
    }

    private static String clean(String value) {
        return StringUtils.defaultString(value).replace(" ", "").replace(" ", "").trim();
    }
}
