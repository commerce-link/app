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

    public static String percent(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
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
