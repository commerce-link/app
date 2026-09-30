package pl.commercelink.web.orders;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.receipts.ReceiptLock;

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
 * the amount 0 and stay pending, while a settled payment needs an amount other than 0. The amounts are text fields read
 * by {@link AmountParser}, so "149,99" is read as well as "149.99" whatever the browser's language.
 * <p>
 * refund tells whether the stored payment goes out to the customer (PaymentDirection.Outgoing). The direction is not a
 * field: a refund stays a refund. The amount field never carries the sign: a refund shows its amount without the minus
 * and is stored negative whichever way it was typed, so the order counts it (Payment#getAppliedAmount) as money that
 * went back; a payment that came in cannot be negative. A refund stored positive by older code is shown without a sign
 * too and is stored negative once its dialog is saved.
 * <p>
 * methodLockedKey is why the payment method is fixed, or null: once the sale has its document (an invoice or a receipt
 * on the order, or an e-receipt being issued or fiscalised, whose request declared the method) the method is shown
 * read-only with this reason and a changed one is refused (owner decision Q3 of 2026-09-29). Amounts, adding and
 * removing payments stay open.
 */
public record OrderPaymentForm(String orderId, int index, String version, boolean pending, boolean refund,
                               PaymentSource source, String name, String amount, String fee, String referenceNo,
                               String bankTransactionNo, String bankTransactionDate, Map<String, String> errors,
                               String refusal, String methodLockedKey) {

    private static final String METHOD_LOCKED = "order.payments.method.locked";

    public OrderPaymentForm {
        errors = errors != null ? errors : Map.of();
    }

    public static OrderPaymentForm of(String orderId, int index, Payment payment) {
        return of(orderId, index, payment, null);
    }

    /** The form of a stored payment; methodLockedKey from {@link #methodLockedKey(Order, ReceiptLock)}. */
    public static OrderPaymentForm of(String orderId, int index, Payment payment, String methodLockedKey) {
        boolean refund = isRefund(payment);
        return new OrderPaymentForm(orderId, index, version(payment), payment.isUnsettled(), refund, payment.getSource(),
                payment.getName(), plain(refund ? Math.abs(payment.getAmount()) : payment.getAmount()),
                payment.getFee() == 0 ? null : plain(payment.getFee()),
                payment.getReferenceNo(), payment.getBankTransactionNo(),
                payment.getBankTransactionDate() == null ? null : payment.getBankTransactionDate().toString(),
                Map.of(), null, methodLockedKey);
    }

    /**
     * Why the order's payment methods are fixed, or null: the predicate of the other "faktura albo paragon" locks
     * (Order#isInvoiced, or the order's e-receipt locking it), worded after the e-receipt's state like them.
     */
    public static String methodLockedKey(Order order, ReceiptLock receiptLock) {
        if (order.isInvoiced()) {
            return METHOD_LOCKED;
        }
        return receiptLock.locks() ? receiptLock.key(METHOD_LOCKED + ".receipt") : null;
    }

    /** The id of the reason under a read-only method. */
    public String methodLockedId() {
        return field("source") + "-locked";
    }

    /** Whether the payment went back to the customer: its amount field carries no sign and it is stored negative. */
    public static boolean isRefund(Payment payment) {
        return payment.getDirection() == PaymentDirection.Outgoing;
    }

    /** The label of the amount field: a refund's amount says it is a refund, as the field has no minus. */
    public String amountLabelKey() {
        return refund ? "order.payments.refund.amount" : "order.payment.amount";
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
        BigDecimal typedAmount = AmountParser.parse(amount);
        if (typedAmount == null) {
            found.put(field("amount"), "order.payments.error.amount");
        } else if (!AmountParser.inRange(typedAmount)) {
            found.put(field("amount"), "order.payments.error.range");
        } else if (typedAmount.signum() < 0 && !refund) {
            // a negative payment would count as a refund without saying so; a refund is added as one
            found.put(field("amount"), "order.payments.error.negative");
        } else if (typedAmount.signum() == 0 && !pending) {
            // the rule of "Dodaj wpłatę": money that arrived is never 0; a payment entered by mistake is removed instead
            found.put(field("amount"), "order.payments.error.amount.zero");
        }
        BigDecimal typedFee = AmountParser.parse(fee);
        if (typedFee == null || typedFee.signum() < 0) {
            found.put(field("fee"), "order.payments.error.fee");
        } else if (!AmountParser.inRange(typedFee)) {
            found.put(field("fee"), "order.payments.error.range");
        }
        if (StringUtils.isNotBlank(bankTransactionDate) && parseDate(bankTransactionDate) == null) {
            found.put(field("bankTransactionDate"), "order.payments.error.date");
        }
        return found;
    }

    /**
     * {@link #validate()} plus the method lock: a method other than saved's is refused while methodLockedKey is set.
     */
    public Map<String, String> validate(Payment saved) {
        Map<String, String> found = new LinkedHashMap<>();
        if (methodLockedKey != null && saved != null && source != saved.getSource()) {
            found.put(field("source"), methodLockedKey);
        }
        validate().forEach(found::putIfAbsent);
        return found;
    }

    /**
     * The payment to store in place of saved: the posted fields over saved's direction, a refund's amount negative.
     * Call only after {@link #validate()} found nothing.
     */
    public Payment toPayment(Payment saved) {
        PaymentDirection direction = saved.getDirection() != null ? saved.getDirection() : PaymentDirection.Incoming;
        BigDecimal typedAmount = AmountParser.parse(amount);
        double stored = direction == PaymentDirection.Outgoing ? typedAmount.abs().negate().doubleValue()
                : typedAmount.doubleValue();
        PaymentSource method = methodLockedKey != null ? saved.getSource() : source;
        return new Payment(StringUtils.trimToNull(referenceNo), StringUtils.trimToNull(name), method, direction,
                stored, AmountParser.parse(fee).doubleValue(),
                StringUtils.trimToNull(bankTransactionNo), parseDate(bankTransactionDate));
    }

    public OrderPaymentForm withErrors(Map<String, String> found) {
        return new OrderPaymentForm(orderId, index, version, pending, refund, source, name, amount, fee, referenceNo,
                bankTransactionNo, bankTransactionDate, found, refusal, methodLockedKey);
    }

    /** A reason the whole form was refused (a cancelled order, a payment changed meanwhile), already translated. */
    public OrderPaymentForm withRefusal(String text) {
        return new OrderPaymentForm(orderId, index, version, pending, refund, source, name, amount, fee, referenceNo,
                bankTransactionNo, bankTransactionDate, errors, text, methodLockedKey);
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

    /** The value of an amount field: a dot and two decimals, whatever the page's language — the precision a save stores. */
    private static String plain(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
