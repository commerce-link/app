package pl.commercelink.web.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The margin of one unit of an order item, shown as an icon with a tooltip next to its price. Margin is the share of
 * the price the store keeps: (price − cost) / price, both gross. The cost carries the item's own VAT rate like the price,
 * so the gross and the net margin are the same number.
 *
 * <p>tone: {@link Tone#UNKNOWN} when the item has no purchase cost yet (a New item, a service without one) — a 100%
 * margin would be a lie; {@link Tone#LOSS} when the price does not cover the cost. percent is null when it cannot be
 * computed (no cost, or a price of zero); profit is gross, profitNet the same profit without the item's VAT (taxRate as
 * stored on the item, e.g. 1.23).
 */
public record ItemMargin(Tone tone, String percent, String profit, String profitNet, String cost) {

    public enum Tone {
        OK, LOSS, UNKNOWN;

        public String key() {
            return "order.items.margin." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static ItemMargin of(double unitPrice, double unitCostGross, double taxRate) {
        if (unitCostGross <= 0) {
            return new ItemMargin(Tone.UNKNOWN, null, null, null, null);
        }
        BigDecimal price = BigDecimal.valueOf(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = BigDecimal.valueOf(unitCostGross).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = price.subtract(cost);
        BigDecimal margin = price.signum() > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP)
                : null;
        BigDecimal profitNet = taxRate > 0 ? profit.divide(BigDecimal.valueOf(taxRate), 2, RoundingMode.HALF_UP) : profit;
        return new ItemMargin(profit.signum() < 0 ? Tone.LOSS : Tone.OK, margin == null ? null : percent(margin),
                Money.format(profit.doubleValue()), Money.format(profitNet.doubleValue()), Money.format(cost.doubleValue()));
    }

    /** "15,0": always one decimal. */
    private static String percent(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pl-PL"));
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('−');
        return new DecimalFormat("0.0", symbols).format(value);
    }
}
