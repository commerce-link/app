package pl.commercelink.payments.statements;

import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Orders and operations for the matcher and booking tests. */
final class StatementOrders {

    private StatementOrders() {
    }

    static Order order(String orderId, double total, Payment... payments) {
        Order order = new Order("store-1");
        order.setOrderId(orderId);
        order.setTotalPrice(total);
        order.setOrderedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        order.setPayments(new ArrayList<>(List.of(payments)));
        return order;
    }

    static Order orderedOn(Order order, int day) {
        order.setOrderedAt(LocalDateTime.of(2026, 10, day, 12, 0));
        return order;
    }

    static Order fromMarketplace(Order order, String marketplace) {
        order.setSource(new OrderSource(marketplace, OrderSourceType.Marketplace));
        return order;
    }

    static Order billedTo(Order order, String name, String surname) {
        BillingDetails billing = new BillingDetails();
        billing.setName(name);
        billing.setSurname(surname);
        order.setBillingDetails(billing);
        return order;
    }

    static Payment pending(PaymentSource source) {
        return new Payment(source);
    }

    static Payment pending(PaymentSource source, String name, String referenceNo, double fee) {
        Payment payment = new Payment(source);
        payment.setName(name);
        payment.setReferenceNo(referenceNo);
        payment.setFee(fee);
        return payment;
    }

    static Payment booked(double amount, String bankTransactionNo) {
        Payment payment = Payment.bankTransfer("title", null, amount);
        payment.setBankTransactionNo(bankTransactionNo);
        payment.setBankTransactionDate(LocalDate.of(2026, 10, 6));
        return payment;
    }

    static BankOperation operation(String number, String amount, String title, String sender) {
        return new BankOperation(10, LocalDate.of(2026, 10, 7), new BigDecimal(amount), title, sender, "", number);
    }
}
