package pl.commercelink.web.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats order amounts the way the order screens show them, so every screen and both languages agree on one grouping and rounding. */
public final class Money {

    private Money() {
    }

    public static String format(double amount) {
        // BigDecimal.valueOf reads the shortest decimal representation of the double, so 2.675 rounds up as a person expects.
        BigDecimal value = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        String digits = new DecimalFormat("#,##0.00", symbols()).format(value.abs());
        return value.signum() < 0 ? "\u2212" + digits : digits;
    }

    /**
     * An amount as the value of a form field bound to a number: a decimal point, no grouping and at least two decimals
     * ("649.00", not "649.0"). A value stored with more decimals (a cost converted from another currency) keeps them,
     * so saving the form never rounds what the operator did not touch.
     */
    public static String input(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount);
        return value.setScale(Math.max(2, value.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY).toPlainString();
    }

    /** An amount rounded to whole grosze, half up: a cost typed or converted with more decimals than money has. */
    public static double round(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** {@link #input(double)} of the amount rounded to two decimals, for a field that only takes whole grosze. */
    public static String inputRounded(double amount) {
        return input(round(amount));
    }

    private static DecimalFormatSymbols symbols() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pl-PL"));
        symbols.setGroupingSeparator('\u00A0');
        symbols.setDecimalSeparator(',');
        return symbols;
    }
}
