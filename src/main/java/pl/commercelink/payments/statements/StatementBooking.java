package pl.commercelink.payments.statements;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Books one statement operation on the orders the operator chose: a payment per order carrying the operation's number
 * and date. Each order is saved on its own through {@link OrderLifecycle#update}, as the payment dialog does, so it may
 * be completed or get its e-receipt; one order that cannot be booked does not hold the others back.
 * <p>
 * The operation's number on an order's payments is what makes a repeated booking (a re-uploaded statement, a retried
 * request) a no-op instead of a second payment; it is checked again on the order as read for the save.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementBooking {

    private static final int REFERENCE_LIMIT = 200;
    private static final int SAVE_ATTEMPTS = 3;

    private final OrdersRepository ordersRepository;
    private final OrderLifecycle orderLifecycle;
    private final ReceiptAttemptService receiptAttemptService;

    public enum Result { BOOKED, ALREADY_BOOKED, FAILED }

    public record OrderOutcome(String orderId, Result result, String messageKey) {
    }

    /** refusalKey is set when nothing was attempted because the request itself is wrong. */
    public record Outcome(BankOperation operation, String refusalKey, List<OrderOutcome> orders) {

        public boolean booked() {
            return refusalKey == null && orders.stream().noneMatch(order -> order.result() == Result.FAILED);
        }
    }

    /**
     * payer holds the payout rows of the payer that sent the operation, or nothing for a direct transfer from the
     * customer; each order of a payout is booked as its first row of the payer says. A direct
     * transfer carries no fees and makes the payment a bank transfer.
     */
    public Outcome book(String storeId, BankOperation operation, List<Allocation> allocations, List<PayoutChannel> payer) {
        String refusal = refusal(operation, allocations, payer);
        if (refusal != null) {
            log.warn("Statement operation {} of store {} was not booked: {}", operation.number(), storeId, refusal);
            return new Outcome(operation, refusal, List.of());
        }
        List<OrderOutcome> orders = allocations.stream()
                .map(allocation -> bookOn(storeId, operation, allocation, payer))
                .toList();
        return new Outcome(operation, null, orders);
    }

    private static String refusal(BankOperation operation, List<Allocation> allocations, List<PayoutChannel> payer) {
        if (!operation.isIncoming() || StringUtils.isBlank(operation.number()) || operation.date() == null) {
            return "statement.book.error.operation";
        }
        if (allocations.isEmpty()) {
            return "statement.book.error.noOrders";
        }
        Set<String> orders = new HashSet<>();
        for (Allocation allocation : allocations) {
            boolean amounts = allocation.amount().signum() > 0 && allocation.fee().signum() >= 0
                    && allocation.fee().compareTo(allocation.amount()) <= 0;
            if (!amounts || !orders.add(allocation.orderId())) {
                return "statement.book.error.allocation";
            }
            if (payer.isEmpty() && allocation.fee().signum() != 0) {
                return "statement.book.error.allocation";
            }
        }
        BigDecimal booked = allocations.stream().map(Allocation::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        return booked.compareTo(operation.amount()) == 0 ? null : "statement.book.error.sum";
    }

    /**
     * Read, apply, save; a save that lost the race with another change of the order (its version moved) reads the order
     * again, so the checks run on what is stored now. The order's save comes before its notifications and events, so a
     * lost race has done nothing yet.
     */
    private OrderOutcome bookOn(String storeId, BankOperation operation, Allocation allocation, List<PayoutChannel> payer) {
        try {
            for (int attempt = 1; ; attempt++) {
                Order order = requireOrder(storeId, allocation.orderId());
                Result result = applyTo(order, operation, allocation, payer);
                try {
                    orderLifecycle.update(order);
                    return new OrderOutcome(allocation.orderId(), result, null);
                } catch (ConditionalCheckFailedException e) {
                    if (attempt == SAVE_ATTEMPTS) {
                        log.warn("Statement operation {} was not booked on order {} of store {}: the order kept changing",
                                operation.number(), allocation.orderId(), storeId);
                        return new OrderOutcome(allocation.orderId(), Result.FAILED, "statement.book.order.stale");
                    }
                }
            }
        } catch (Refusal refusal) {
            if (refusal.result == Result.FAILED) {
                log.warn("Statement operation {} was not booked on order {} of store {}: {}", operation.number(),
                        allocation.orderId(), storeId, refusal.getMessage());
            }
            return new OrderOutcome(allocation.orderId(), refusal.result, refusal.getMessage());
        } catch (RuntimeException e) {
            log.error("Statement operation {} could not be booked on order {} of store {}", operation.number(),
                    allocation.orderId(), storeId, e);
            return new OrderOutcome(allocation.orderId(), Result.FAILED, "statement.book.order.error");
        }
    }

    private Order requireOrder(String storeId, String orderId) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            throw new Refusal(Result.FAILED, "statement.book.order.missing");
        }
        return order;
    }

    /** Fills the order's payment, or refuses before anything changed: an order that is not booked is not saved. */
    private Result applyTo(Order order, BankOperation operation, Allocation allocation, List<PayoutChannel> payer) {
        if (order.isClosed()) {
            throw new Refusal(Result.FAILED, "statement.book.order.closed");
        }
        String number = operation.number().trim().toUpperCase(Locale.ROOT);
        if (order.getPayments().stream().anyMatch(payment -> payment.getBankTransactionNo() != null
                && number.equals(payment.getBankTransactionNo().trim().toUpperCase(Locale.ROOT)))) {
            throw new Refusal(Result.ALREADY_BOOKED, "statement.book.order.already");
        }
        Payment pending = order.getPendingPayment();
        Payment target;
        double keptFee = 0;
        if (!payer.isEmpty()) {
            // read before the payment is filled: the row is chosen by the method and name the order waits with
            PayoutChannel row = PayoutChannels.rowOf(payer, order).orElse(null);
            // a payer that does not deduct its fee from the transfer still charged it: the recorded fee stays
            keptFee = (row == null || !row.isNetOfFees()) && pending != null ? pending.getFee() : 0;
            target = pending != null ? pending : added(order, PayoutChannels.sourceOf(row), row == null ? null : row.getProvider());
        } else {
            if (pending != null && pending.getSource() != null && pending.getSource() != PaymentSource.BankTransfer) {
                // the customer paid by transfer instead of the method the order waited for (an online payment that
                // failed): the payment becomes a transfer, which an issued invoice or e-receipt no longer allows
                if (order.isInvoiced() || receiptAttemptService.locksOrder(order)) {
                    throw new Refusal(Result.FAILED, "statement.book.order.methodLocked");
                }
                pending.setSource(PaymentSource.BankTransfer);
                pending.setName(null);
                pending.setReferenceNo(null);
                pending.setFee(0);
            }
            target = pending != null ? pending : added(order, PaymentSource.BankTransfer, null);
            if (target.getSource() == null) {
                target.setSource(PaymentSource.BankTransfer);
            }
        }
        target.setDirection(PaymentDirection.Incoming);
        target.setAmount(allocation.amount().doubleValue());
        target.setFee(StatementAmounts.of(keptFee).add(allocation.fee()).doubleValue());
        if (StringUtils.isBlank(target.getReferenceNo())) {
            target.setReferenceNo(StringUtils.trimToNull(StringUtils.abbreviate(operation.title(), REFERENCE_LIMIT)));
        }
        target.setBankTransactionNo(operation.number());
        target.setBankTransactionDate(operation.date());
        return Result.BOOKED;
    }

    private static Payment added(Order order, PaymentSource source, String name) {
        Payment payment = new Payment(source);
        payment.setName(StringUtils.trimToNull(name));
        order.addPayment(payment);
        return payment;
    }

    private static final class Refusal extends RuntimeException {

        private final Result result;

        Refusal(Result result, String messageKey) {
            super(messageKey, null, false, false);
            this.result = result;
        }
    }
}
