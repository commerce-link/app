package pl.commercelink.web.dtos;

import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.web.orders.AmountParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The shared "Dodaj wpłatę" dialog (fragments/add-payment-modal.html) of an order or a delivery. The amounts are text
 * fields read by {@link AmountParser}, so "149,99" and "1 499,99" are read the same in every browser; a blank fee is 0.
 * Call {@link #validate()} before {@link #amount()} and {@link #fee()}.
 * <p>
 * target, an order's only, is the pending payment the money goes to: its index among the order's payments, or
 * {@link #NEW_PAYMENT} for money the order does not wait for. A form without it (an older page) leaves the choice to
 * Order.pendingPaymentFor by its method.
 */
public class AddPaymentForm {

    public static final String NEW_PAYMENT = "new";

    private String bankAmount;
    private String processingFee;
    private String target;
    private boolean feeIncluded;
    private PaymentSource source;
    private PaymentDirection direction;
    private String referenceNo;
    private String name;
    private String bankTransactionNo;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate bankTransactionDate;
    /** The Payments page's own address when the dialog was opened there (PaymentsReturn); null elsewhere. */
    private String returnTo;

    /**
     * Why the amounts cannot be saved, as a message key, or null: the amount is a number other than 0 below the limit,
     * the fee is blank or a number 0 or more below it. The sign of the amount is the caller's rule (an order's refund
     * versus a delivery's payout).
     */
    public String validate() {
        BigDecimal amount = AmountParser.parse(bankAmount);
        if (amount == null) {
            return "error.message.payment.amount.format";
        }
        if (!AmountParser.inRange(amount)) {
            return "error.message.payment.range";
        }
        if (amount.signum() == 0) {
            return "error.message.payment.amount.invalid";
        }
        BigDecimal fee = AmountParser.parse(processingFee);
        if (fee == null || fee.signum() < 0) {
            return "error.message.payment.fee.invalid";
        }
        if (!AmountParser.inRange(fee)) {
            return "error.message.payment.range";
        }
        return null;
    }

    /** The money is a payment of its own: the order waits for none like it. */
    public boolean isNewPayment() {
        return NEW_PAYMENT.equals(StringUtils.trim(target));
    }

    /** A pending payment was chosen for the money. */
    public boolean hasTarget() {
        return StringUtils.isNotBlank(target) && !isNewPayment();
    }

    /** The chosen pending payment of order, or null when the index names none, or one no longer waiting. */
    public Payment chosenPending(Order order) {
        List<Payment> payments = order.getPayments();
        try {
            int index = Integer.parseInt(StringUtils.trim(target));
            return index >= 0 && index < payments.size() && payments.get(index).isUnsettled() ? payments.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The amount as typed, to the cent. */
    public double amount() {
        return AmountParser.parse(bankAmount).doubleValue();
    }

    /** The fee as typed, to the cent; 0 when blank. */
    public double fee() {
        return AmountParser.parse(processingFee).doubleValue();
    }

    public String getBankAmount() {
        return bankAmount;
    }

    public void setBankAmount(String bankAmount) {
        this.bankAmount = bankAmount;
    }

    public String getProcessingFee() {
        return processingFee;
    }

    public void setProcessingFee(String processingFee) {
        this.processingFee = processingFee;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean isFeeIncluded() {
        return feeIncluded;
    }

    public void setFeeIncluded(boolean feeIncluded) {
        this.feeIncluded = feeIncluded;
    }

    public PaymentSource getSource() {
        return source;
    }

    public void setSource(PaymentSource source) {
        this.source = source;
    }

    public PaymentDirection getDirection() {
        return direction;
    }

    public void setDirection(PaymentDirection direction) {
        this.direction = direction;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBankTransactionNo() {
        return bankTransactionNo;
    }

    public void setBankTransactionNo(String bankTransactionNo) {
        this.bankTransactionNo = bankTransactionNo;
    }

    public LocalDate getBankTransactionDate() {
        return bankTransactionDate;
    }

    public void setBankTransactionDate(LocalDate bankTransactionDate) {
        this.bankTransactionDate = bankTransactionDate;
    }

    public String getReturnTo() {
        return returnTo;
    }

    public void setReturnTo(String returnTo) {
        this.returnTo = returnTo;
    }
}
