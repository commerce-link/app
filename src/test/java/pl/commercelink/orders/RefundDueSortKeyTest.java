package pl.commercelink.orders;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefundDueSortKeyTest {

    private static Order order(OrderStatus status) {
        Order order = new Order("store-1");
        order.setStatus(status);
        order.setTotalPrice(1249);
        order.setOrderedAt(LocalDateTime.of(2026, 11, 3, 14, 25));
        return order;
    }

    private static Payment refund(double amount) {
        return new Payment("REF-2", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -amount, 0, null, null);
    }

    @Test
    void aCancelledOrderHoldingAPaymentIsKeyedByItsOrderDateAsStored() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));

        // when / then: zero seconds are written out, as in the stored orderedAt
        assertThat(RefundDueSortKey.of(order)).isEqualTo("2026-11-03T14:25:00");
        assertThat(order.getRefundDueSortKey()).isEqualTo("2026-11-03T14:25:00");
    }

    @Test
    void anOpenOrderHasNoKeyEvenWhenPaid() {
        // given
        Order order = order(OrderStatus.New);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));

        // when / then
        assertThat(order.getRefundDueSortKey()).isNull();
    }

    @Test
    void aCancelledOrderThatWasNeverPaidHasNoKey() {
        // given
        Order order = order(OrderStatus.Cancelled);

        // when / then
        assertThat(order.getRefundDueSortKey()).isNull();
    }

    @Test
    void theKeyGoesOnceTheRefundIsRecorded() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));
        order.addPayment(refund(1249));

        // when / then
        assertThat(order.getRefundDueSortKey()).isNull();
    }

    @Test
    void aPartialRefundKeepsTheKey() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));
        order.addPayment(refund(1000));

        // when / then
        assertThat(order.getRefundDueSortKey()).isEqualTo("2026-11-03T14:25:00");
    }

    @Test
    void anOrderWithoutAnOrderDateSortsAsTheOldest() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));
        order.setOrderedAt(null);

        // when / then
        assertThat(order.getRefundDueSortKey()).isEqualTo(RefundDueSortKey.UNDATED).isLessThan("2000-01-01T00:00:00");
    }
}
