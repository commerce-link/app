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

    private static DecimalFormatSymbols symbols() {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pl-PL"));
        symbols.setGroupingSeparator('\u00A0');
        symbols.setDecimalSeparator(',');
        return symbols;
    }
}
