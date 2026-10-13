package pl.commercelink.web.reports;

import pl.commercelink.web.orders.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Number formats of the reports page: amounts like the order screens, whole amounts on the cards, compact axis values. */
public final class ReportNumbers {

    public record Axis(String number, String unitKey) {
    }

    private ReportNumbers() {
    }

    public static String amount(double value) {
        return Money.format(value);
    }

    public static String wholeAmount(double value) {
        String formatted = Money.format(BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).doubleValue());
        return formatted.substring(0, formatted.length() - 3);
    }

    /** A count with the same thousands separator as the amounts next to it ("1 291", not "1291"). */
    public static String count(long value) {
        return wholeAmount(value);
    }

    public static String percent(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',').replace('-', '\u2212') + "%";
    }

    /**
     * A tick of an axis whose step is {@code step}: below a step of a thousand every tick is a plain number, so "1 000"
     * does not stand as "1 tys." over "750"; from a thousand up the ticks are compact.
     */
    public static Axis axis(double value, double step) {
        if (step < 1_000) {
            return new Axis(value == Math.rint(value) ? count(Math.round(value)) : compact(value), null);
        }
        return axis(value);
    }

    public static Axis axis(double value) {
        if (value >= 1_000_000) {
            return new Axis(compact(value / 1_000_000), "reports.axis.millions");
        }
        if (value >= 1_000) {
            return new Axis(compact(value / 1_000), "reports.axis.thousands");
        }
        return new Axis(compact(value), null);
    }

    private static String compact(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
