package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAttentionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

    static Order order(OrderStatus status, LocalDate estimatedShippingAt, LocalDate preferredShippingAt, double total, double paid) {
        Order order = new Order("store-1");
        order.setOrderId("o-" + status + "-" + estimatedShippingAt);
        order.setStatus(status);
        order.setEstimatedShippingAt(estimatedShippingAt);
        order.setPreferredShippingAt(preferredShippingAt);
        order.setTotalPrice(total);
        Payment payment = new Payment(PaymentSource.BankTransfer);
        payment.setAmount(paid);
        order.setPayments(List.of(payment));
        return order;
    }

    @Test
    void overdueIsDueBeforeTodayAndNotYetShipping() {
        assertThat(OrderAttention.Overdue.matches(order(OrderStatus.New, TODAY.minusDays(2), null, 10, 10), TODAY)).isTrue();
        assertThat(OrderAttention.Overdue.matches(order(OrderStatus.Shipping, TODAY.minusDays(2), null, 10, 10), TODAY)).isFalse();
        assertThat(OrderAttention.Overdue.matches(order(OrderStatus.New, TODAY, null, 10, 10), TODAY)).isFalse();
        assertThat(OrderAttention.Overdue.matches(order(OrderStatus.New, null, null, 10, 10), TODAY)).isFalse();
    }

    @Test
    void preferredDateWinsOverEstimatedDate() {
        Order order = order(OrderStatus.Assembled, TODAY.minusDays(3), TODAY, 10, 10);
        assertThat(OrderAttention.Overdue.matches(order, TODAY)).isFalse();
        assertThat(OrderAttention.Today.matches(order, TODAY)).isTrue();
    }

    @Test
    void decideIsNewOrBlocked() {
        assertThat(OrderAttention.Decide.matches(order(OrderStatus.Blocked, null, null, 10, 10), TODAY)).isTrue();
        assertThat(OrderAttention.Decide.matches(order(OrderStatus.Assembly, null, null, 10, 10), TODAY)).isFalse();
    }

    @Test
    void unpaidIsOpenWithMissingPayment() {
        assertThat(OrderAttention.Unpaid.matches(order(OrderStatus.Delivered, null, null, 100, 40), TODAY)).isTrue();
        assertThat(OrderAttention.Unpaid.matches(order(OrderStatus.Completed, null, null, 100, 40), TODAY)).isFalse();
        assertThat(OrderAttention.Unpaid.matches(order(OrderStatus.New, null, null, 100, 100), TODAY)).isFalse();
    }

    @Test
    void parseIsCaseInsensitiveAndRejectsUnknown() {
        assertThat(OrderAttention.parse("OVERDUE")).contains(OrderAttention.Overdue);
        assertThat(OrderAttention.parse("nope")).isEmpty();
        assertThat(OrderAttention.parse(null)).isEmpty();
        assertThat(OrderAttention.Today.param()).isEqualTo("today");
    }
}
