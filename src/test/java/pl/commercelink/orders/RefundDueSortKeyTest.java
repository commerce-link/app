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
    void aCancelledOrderHoldingAPaymentIsKeyedByWhenItWasCancelled() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.setCancelledAt(LocalDateTime.of(2026, 11, 10, 9, 0));
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));

        // when / then: zero seconds are written out, as in the stored dates
        assertThat(RefundDueSortKey.of(order)).isEqualTo("2026-11-10T09:00:00");
        assertThat(order.getRefundDueSortKey()).isEqualTo("2026-11-10T09:00:00");
    }

    @Test
    void anOrderCancelledBeforeTheDateWasRecordedIsKeyedByItsOrderDate() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));

        // when / then
        assertThat(order.getRefundDueSortKey()).isEqualTo("2026-11-03T14:25:00");
    }

    @Test
    void ordersCancelledBeforeTheDateWasRecordedComeBeforeThoseCancelledSince() {
        // given: both placed the same day, one cancelled before the release that records the date and one after it
        Order before = order(OrderStatus.Cancelled);
        before.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));
        Order since = order(OrderStatus.Cancelled);
        since.setCancelledAt(LocalDateTime.of(2026, 11, 20, 8, 30));
        since.addPayment(Payment.bankTransfer("REF-2", "Ola", 1249));

        // when / then
        assertThat(before.getRefundDueSortKey()).isLessThan(since.getRefundDueSortKey());
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
    void aRefundRecordedAboveWhatWasPaidInLeavesNoRefundToMake() {
        // given: the refund was entered in full with its fee on top, so the balance tips the other way
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));
        order.addPayment(new Payment("REF-2", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -1249, 10, null, null));

        // when / then
        assertThat(order.getUnpaidAmount()).isEqualTo(10);
        assertThat(order.awaitsRefund()).isFalse();
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
    void anOrderWithNeitherDateSortsAsTheOldest() {
        // given
        Order order = order(OrderStatus.Cancelled);
        order.addPayment(Payment.bankTransfer("REF-1", "Jan", 1249));
        order.setOrderedAt(null);

        // when / then
        assertThat(order.getRefundDueSortKey()).isEqualTo(RefundDueSortKey.UNDATED).isLessThan("2000-01-01T00:00:00");
    }
}
