package pl.commercelink.payments.statements;

import java.math.BigDecimal;

/**
 * The part of an operation booked on one order: the payment's amount and fee as the order counts them. The operation's
 * amount is the sum of amount less fee over its allocations; a payout pays an order's amount less the fee the payer kept.
 */
public record Allocation(String orderId, BigDecimal amount, BigDecimal fee) {

    public BigDecimal net() {
        return amount.subtract(fee);
    }
}
