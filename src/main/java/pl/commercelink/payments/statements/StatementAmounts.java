package pl.commercelink.payments.statements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Amounts as banks print them in CSV exports: "1 234,56", "-1234.56", "+640,00 PLN", "2 310,50-". The decimal mark is
 * the statement format's, so the other mark is a thousands separator and dropped.
 */
public final class StatementAmounts {

    private static final Pattern CURRENCY = Pattern.compile("(?i)(PLN|EUR|USD|GBP|CHF|zł|zl)$");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    private StatementAmounts() {
    }

    /** The amount to the grosz, or null when the cell holds no number. */
    public static BigDecimal parse(String cell, boolean decimalComma) {
        if (cell == null) {
            return null;
        }
        String text = cell.replaceAll("[\\s\\u00a0\\u202f'\"]", "").replace('\u2212', '-');
        text = CURRENCY.matcher(text).replaceAll("");
        if (text.isEmpty()) {
            return null;
        }
        boolean negative = false;
        if (text.startsWith("-") || text.endsWith("-")) {
            negative = true;
            text = text.startsWith("-") ? text.substring(1) : text.substring(0, text.length() - 1);
        } else if (text.startsWith("+")) {
            text = text.substring(1);
        }
        text = decimalComma ? text.replace(".", "").replace(',', '.') : text.replace(",", "");
        if (!NUMBER.matcher(text).matches()) {
            return null;
        }
        BigDecimal value = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        return negative ? value.negate() : value;
    }

    /** A stored double amount (orders keep doubles) to the grosz, so amounts from both sides compare exactly. */
    public static BigDecimal of(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
