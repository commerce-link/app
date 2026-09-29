package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One payment of an order as its edit form shows it: in the payment's own dialog of the payments card and on the
 * payment page without JavaScript. index is the payment's position in the order and version a fingerprint of the
 * payment the form was rendered from: a save or a removal whose payment has changed since (another operator, a bank
 * statement import) is refused instead of overwriting it. A new payment is not made here: "Dodaj wpłatę" (the shared
 * add-payment dialog) fills the pending payment first, which this form cannot do.
 * <p>
 * pending tells whether the stored payment is the one the order waits for (Payment.isUnsettled, amount 0): it may keep
 * the amount 0 and stay pending, while a settled payment needs an amount other than 0. The amounts are posted as typed,
 * so "149,99" is read as well as "149.99". The sign is stored as typed too: a refund is typed with a minus in "Dodaj
 * wpłatę", supplier payouts are stored positive, and the page shows one minus either way. The direction is not a field:
 * a refund stays a refund.
 */
public record OrderPaymentForm(String orderId, int index, String version, boolean pending, PaymentSource source,
                               String name, String amount, String fee, String referenceNo, String bankTransactionNo,
                               String bankTransactionDate, Map<String, String> errors, String refusal) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal LIMIT = BigDecimal.valueOf(10_000_000);

    public OrderPaymentForm {
        errors = errors != null ? errors : Map.of();
    }

    public static OrderPaymentForm of(String orderId, int index, Payment payment) {
        return new OrderPaymentForm(orderId, index, version(payment), payment.isUnsettled(), payment.getSource(),
                payment.getName(), plain(payment.getAmount()), payment.getFee() == 0 ? null : plain(payment.getFee()),
                payment.getReferenceNo(), payment.getBankTransactionNo(),
                payment.getBankTransactionDate() == null ? null : payment.getBankTransactionDate().toString(),
                Map.of(), null);
    }

    /** What an edit is checked against: every field the form shows. A blank text and no text are the same. */
    public static String version(Payment payment) {
        String fields = String.join("|", String.valueOf(payment.getSource()),
                Objects.toString(StringUtils.trimToNull(payment.getName()), ""), Double.toString(payment.getAmount()),
                Double.toString(payment.getFee()), Objects.toString(StringUtils.trimToNull(payment.getReferenceNo()), ""),
                Objects.toString(StringUtils.trimToNull(payment.getBankTransactionNo()), ""),
                Objects.toString(payment.getBankTransactionDate(), ""));
        return Integer.toHexString(fields.hashCode());
    }

    /** The payment's number as the card counts them, from 1. */
    public int number() {
        return index + 1;
    }

    /** The part of the dialog's and the fields' ids that tells the forms of one page apart. */
    public String key() {
        return String.valueOf(index);
    }

    public String dialogId() {
        return "payment-dialog-" + key();
    }

    /** The id of a field: payment-0-amount. The posted name is the property alone. */
    public String field(String property) {
        return "payment-" + key() + "-" + property;
    }

    /** The message key of the field's error, or null. */
    public String error(String property) {
        return errors.get(field(property));
    }

    /** The payment methods of the form's select. */
    public List<OrderLabels.Option<PaymentSource>> sources() {
        return OrderLabels.Option.of(PaymentSource.values(), OrderLabels::paymentSource);
    }

    /** Field id to message key, in the order of the form. */
    public Map<String, String> validate() {
        Map<String, String> found = new LinkedHashMap<>();
        // the pending payment carries the method the customer chose; without one it would no longer say how to pay
        if (source == null) {
            found.put(field("source"), "order.payments.error.source");
        }
        BigDecimal typedAmount = parseAmount(amount);
        if (typedAmount == null) {
            found.put(field("amount"), "order.payments.error.amount");
        } else if (!inRange(typedAmount)) {
            found.put(field("amount"), "order.payments.error.range");
        } else if (typedAmount.signum() == 0 && !pending) {
            // the rule of "Dodaj wpłatę": money that arrived is never 0; a payment entered by mistake is removed instead
            found.put(field("amount"), "order.payments.error.amount.zero");
        }
        BigDecimal typedFee = parseAmount(fee);
        if (typedFee == null || typedFee.signum() < 0) {
            found.put(field("fee"), "order.payments.error.fee");
        } else if (!inRange(typedFee)) {
            found.put(field("fee"), "order.payments.error.range");
        }
        if (StringUtils.isNotBlank(bankTransactionDate) && parseDate(bankTransactionDate) == null) {
            found.put(field("bankTransactionDate"), "order.payments.error.date");
        }
        return found;
    }

    /**
     * The payment to store in place of saved: the posted fields over saved's direction. Call only after
     * {@link #validate()} found nothing.
     */
    public Payment toPayment(Payment saved) {
        PaymentDirection direction = saved.getDirection() != null ? saved.getDirection() : PaymentDirection.Incoming;
        return new Payment(StringUtils.trimToNull(referenceNo), StringUtils.trimToNull(name), source, direction,
                parseAmount(amount).doubleValue(), parseAmount(fee).doubleValue(),
                StringUtils.trimToNull(bankTransactionNo), parseDate(bankTransactionDate));
    }

    public OrderPaymentForm withErrors(Map<String, String> found) {
        return new OrderPaymentForm(orderId, index, version, pending, source, name, amount, fee, referenceNo,
                bankTransactionNo, bankTransactionDate, found, refusal);
    }

    /** A reason the whole form was refused (a cancelled order, a payment changed meanwhile), already translated. */
    public OrderPaymentForm withRefusal(String text) {
        return new OrderPaymentForm(orderId, index, version, pending, source, name, amount, fee, referenceNo,
                bankTransactionNo, bankTransactionDate, errors, text);
    }

    /**
     * The amount as it will be stored, to the grosz: "149,99", "149.99", "1 499,99", "-100"; a blank field is 0 (a
     * cleared fee, the pending payment's amount); null for anything that is not a number. Validation checks this
     * rounded value, so "0.001" counts as 0. A value far outside the range is returned unrounded: rounding
     * "1e-999999999" or "1e999999999" to two decimals would build a number of a billion digits, and
     * {@link #inRange} refuses the large one anyway.
     */
    static BigDecimal parseAmount(String value) {
        if (StringUtils.isBlank(value)) {
            return ZERO;
        }
        String typed = value.replaceAll("[\\s\\u00a0\\u202f]", "").replace(',', '.');
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
    static boolean inRange(BigDecimal value) {
        return value.abs().compareTo(LIMIT) < 0 && Double.isFinite(value.doubleValue());
    }

    private static LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * The value of a number field: a dot and two decimals, whatever the page's language — the precision a save
     * stores.
     */
    private static String plain(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
