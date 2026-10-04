package pl.commercelink.web.orders;

import pl.commercelink.stores.MarginConfiguration;

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
 * margin would be a lie; {@link Tone#LOSS} when the price does not cover the cost, always marked; {@link Tone#LOW} when
 * the margin is below the store's threshold for the item's category (Settings › Margins). percent is null when it
 * cannot be computed (no cost, or a price of zero); profit is gross, profitNet the same profit without the item's VAT
 * (taxRate as stored on the item, e.g. 1.23); threshold and thresholdCategory name the threshold the item was compared
 * with (thresholdCategory null: the store's default), for the tooltip.
 */
public record ItemMargin(Tone tone, String percent, String profit, String profitNet, String cost, String threshold,
                         String thresholdCategory) {

    public enum Tone {
        OK, LOW, LOSS, UNKNOWN;

        public String key() {
            return "order.items.margin." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static ItemMargin of(double unitPrice, double unitCostGross, double taxRate) {
        return of(unitPrice, unitCostGross, taxRate, null);
    }

    public static ItemMargin of(double unitPrice, double unitCostGross, double taxRate,
                                MarginConfiguration.Threshold threshold) {
        String thresholdText = threshold == null ? null : percent(BigDecimal.valueOf(threshold.percent()), "0.##");
        String thresholdCategory = threshold == null ? null : threshold.category();
        if (unitCostGross <= 0) {
            return new ItemMargin(Tone.UNKNOWN, null, null, null, null, thresholdText, thresholdCategory);
        }
        BigDecimal price = BigDecimal.valueOf(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = BigDecimal.valueOf(unitCostGross).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = price.subtract(cost);
        BigDecimal margin = price.signum() > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(price, 1, RoundingMode.HALF_UP)
                : null;
        BigDecimal profitNet = taxRate > 0 ? profit.divide(BigDecimal.valueOf(taxRate), 2, RoundingMode.HALF_UP) : profit;
        // the shown margin (one decimal) is what is compared, so "10,0%" is never called low against a 10% threshold
        Tone tone = profit.signum() < 0 ? Tone.LOSS
                : threshold != null && margin != null && margin.compareTo(BigDecimal.valueOf(threshold.percent())) < 0 ? Tone.LOW
                : Tone.OK;
        return new ItemMargin(tone, margin == null ? null : percent(margin, "0.0"), Money.format(profit.doubleValue()),
                Money.format(profitNet.doubleValue()), Money.format(cost.doubleValue()), thresholdText, thresholdCategory);
    }

    /** The tooltip's message key: a low margin says whether the threshold was its category's or the store's default. */
    public String messageKey() {
        return tone == Tone.LOW && thresholdCategory != null ? tone.key() + ".category" : tone.key();
    }

    /** "15,0" for a margin (always one decimal), "12,5" or "20" for a threshold (as typed). */
    private static String percent(BigDecimal value, String pattern) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pl-PL"));
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('−');
        return new DecimalFormat(pattern, symbols).format(value);
    }
}
