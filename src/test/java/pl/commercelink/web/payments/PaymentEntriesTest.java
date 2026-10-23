package pl.commercelink.web.payments;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryOrderStatus;
import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentEntriesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    static Delivery delivery(double cost, LocalDate ordered, int terms) {
        Delivery d = new Delivery("store-1", "ZS/1", "Acme");
        d.setTax(1.0); // the rate is a multiplier (Price.DEFAULT_VAT_RATE = 1.23): 1.0 makes gross = net
        d.setTotalCost(cost);
        d.setOrderedAt(ordered == null ? null : ordered.atTime(9, 0));
        d.setPaymentTerms(terms);
        return d;
    }

    static Payment paid(double amount) {
        Payment p = new Payment(PaymentSource.BankTransfer);
        p.setDirection(PaymentDirection.Outgoing);
        p.setAmount(amount);
        return p;
    }

    static Order order(double total, OrderStatus status, PaymentSource method, double paidAmount) {
        Order o = new Order();
        o.setStoreId("store-1");
        o.setOrderId("b71e0a93-0000-0000-0000-000000000000");
        o.setTotalPrice(total);
        o.setStatus(status);
        List<Payment> payments = new ArrayList<>();
        Payment pending = new Payment(method);
        payments.add(pending);
        if (paidAmount != 0) {
            Payment p = new Payment(method);
            p.setDirection(PaymentDirection.Incoming);
            p.setAmount(paidAmount);
            payments.add(p);
        }
        o.setPayments(payments);
        return o;
    }

    @Test
    void failedAwaitingApprovalAndZeroDeliveriesAreNotPayables() {
        // given
        Delivery failed = delivery(100, TODAY, 0);
        failed.setOrderStatus(DeliveryOrderStatus.FAILED);
        Delivery approval = delivery(100, TODAY, 0);
        approval.setOrderStatus(DeliveryOrderStatus.AWAITING_APPROVAL);
        Delivery zero = delivery(0, TODAY, 0);

        // when / then
        assertThat(PayableEntry.of(failed)).isEmpty();
        assertThat(PayableEntry.of(approval)).isEmpty();
        assertThat(PayableEntry.of(zero)).isEmpty();
    }

    @Test
    void payableStandingFollowsWhatWasPaid() {
        // given
        Delivery unpaid = delivery(100, TODAY, 14);
        Delivery underpaid = delivery(100, TODAY, 14);
        underpaid.addPayment(paid(40));
        Delivery overpaid = delivery(100, TODAY, 14);
        overpaid.addPayment(paid(120));
        Delivery settled = delivery(100, TODAY, 14);
        settled.addPayment(paid(100));

        // when / then
        assertThat(PayableEntry.of(unpaid).orElseThrow().standing()).isEqualTo(PaymentsAmounts.Standing.UNPAID);
        assertThat(PayableEntry.of(underpaid).orElseThrow().standing()).isEqualTo(PaymentsAmounts.Standing.UNDERPAID);
        PayableEntry refund = PayableEntry.of(overpaid).orElseThrow();
        assertThat(refund.standing()).isEqualTo(PaymentsAmounts.Standing.REFUND);
        assertThat(refund.amount()).isEqualTo(20.0);
        assertThat(PayableEntry.of(settled)).isEmpty();
    }

    @Test
    void payableTilesUseThePaymentDueDate() {
        // given
        PayableEntry overdue = PayableEntry.of(delivery(100, TODAY.minusDays(20), 14)).orElseThrow();
        PayableEntry today = PayableEntry.of(delivery(100, TODAY.minusDays(14), 14)).orElseThrow();
        Delivery over = delivery(100, TODAY.minusDays(30), 0);
        over.addPayment(paid(150));
        PayableEntry refund = PayableEntry.of(over).orElseThrow();

        // when / then
        assertThat(PaymentFocus.OVERDUE.matches(overdue, TODAY)).isTrue();
        assertThat(PaymentFocus.TODAY.matches(today, TODAY)).isTrue();
        assertThat(PaymentFocus.OVERDUE.matches(refund, TODAY)).as("a refund is not a debt past due").isFalse();
        assertThat(PaymentFocus.REFUND.matches(refund, TODAY)).isTrue();
    }

    @Test
    void payableWithoutOrderDateHasNoDueDateAndIsNotOverdue() {
        // given
        PayableEntry entry = PayableEntry.of(delivery(100, null, 7)).orElseThrow();

        // when / then
        assertThat(entry.due()).isNull();
        assertThat(PaymentFocus.OVERDUE.matches(entry, TODAY)).isFalse();
        assertThat(PaymentFocus.TODAY.matches(entry, TODAY)).isFalse();
    }

    @Test
    void shippedOrderWithoutMoneyIsUrgent() {
        // given
        Order order = order(3499, OrderStatus.Shipping, PaymentSource.BankTransfer, 0);

        // when
        ReceivableEntry entry = ReceivableEntry.of(order, TODAY).orElseThrow();

        // then
        assertThat(entry.urgency()).isEqualTo(ReceivableEntry.Urgency.SHIPPED_UNPAID);
        assertThat(PaymentFocus.OVERDUE.matches(entry)).isTrue();
    }

    @Test
    void cashOnDeliveryIsNormalUntilDelivered() {
        // given
        Order onTheWay = order(1249, OrderStatus.Shipping, PaymentSource.CashOnDelivery, 0);
        Order delivered = order(1249, OrderStatus.Delivered, PaymentSource.CashOnDelivery, 0);

        // when
        ReceivableEntry inTransit = ReceivableEntry.of(onTheWay, TODAY).orElseThrow();
        ReceivableEntry unsettled = ReceivableEntry.of(delivered, TODAY).orElseThrow();

        // then
        assertThat(inTransit.standing()).isEqualTo(PaymentsAmounts.Standing.COD);
        assertThat(inTransit.urgency()).isEqualTo(ReceivableEntry.Urgency.NONE);
        assertThat(unsettled.urgency()).isEqualTo(ReceivableEntry.Urgency.COD_UNSETTLED);
        assertThat(PaymentFocus.OVERDUE.matches(unsettled)).isTrue();
    }

    @Test
    void shippingDueDateDrivesTheWarning() {
        // given
        Order today = order(899, OrderStatus.Realization, PaymentSource.BankTransfer, 0);
        today.setEstimatedShippingAt(TODAY);
        Order inTwoDays = order(2000, OrderStatus.Assembly, PaymentSource.BankTransfer, 4000 - 2000);
        inTwoDays.setTotalPrice(6000);
        inTwoDays.setEstimatedShippingAt(TODAY.plusDays(2));
        Order late = order(500, OrderStatus.New, PaymentSource.Installments, 0);
        late.setEstimatedShippingAt(TODAY.minusDays(1));
        Order later = order(500, OrderStatus.New, PaymentSource.BankTransfer, 0);
        later.setEstimatedShippingAt(TODAY.plusDays(7));

        // when / then
        assertThat(ReceivableEntry.of(today, TODAY).orElseThrow().urgency()).isEqualTo(ReceivableEntry.Urgency.SHIP_TODAY);
        assertThat(PaymentFocus.TODAY.matches(ReceivableEntry.of(today, TODAY).orElseThrow())).isTrue();
        ReceivableEntry partial = ReceivableEntry.of(inTwoDays, TODAY).orElseThrow();
        assertThat(partial.urgency()).isEqualTo(ReceivableEntry.Urgency.SHIP_IN_TWO_DAYS);
        assertThat(partial.standing()).isEqualTo(PaymentsAmounts.Standing.UNDERPAID);
        assertThat(PaymentFocus.UNDERPAID.matches(partial)).isTrue();
        assertThat(ReceivableEntry.of(late, TODAY).orElseThrow().urgency()).isEqualTo(ReceivableEntry.Urgency.SHIP_OVERDUE);
        assertThat(PaymentFocus.OVERDUE.matches(ReceivableEntry.of(late, TODAY).orElseThrow()))
                .as("a missed shipping date is a warning, not money overdue").isFalse();
        assertThat(ReceivableEntry.of(later, TODAY).orElseThrow().urgency()).isEqualTo(ReceivableEntry.Urgency.NONE);
    }

    @Test
    void overpaidOrderIsARefund() {
        // given
        Order order = order(1299, OrderStatus.Shipping, PaymentSource.OnlinePayment, 1349);

        // when
        ReceivableEntry entry = ReceivableEntry.of(order, TODAY).orElseThrow();

        // then
        assertThat(entry.standing()).isEqualTo(PaymentsAmounts.Standing.REFUND);
        assertThat(entry.amount()).isEqualTo(50.0);
        assertThat(entry.urgency()).isEqualTo(ReceivableEntry.Urgency.NONE);
        assertThat(PaymentFocus.REFUND.matches(entry)).isTrue();
    }

    @Test
    void receivableWithFloatNoiseIsSkipped() {
        // given: 0.1 + 0.2 paid against 0.3 leaves 5.5e-17
        Order order = order(0.3, OrderStatus.New, PaymentSource.BankTransfer, 0.1);
        Payment second = new Payment(PaymentSource.BankTransfer);
        second.setDirection(PaymentDirection.Incoming);
        second.setAmount(0.2);
        order.getPayments().add(second);

        // when / then
        assertThat(ReceivableEntry.of(order, TODAY)).isEmpty();
    }
}
