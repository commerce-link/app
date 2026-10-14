package pl.commercelink.payments.statements;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.receipts.ReceiptAttemptService;
import pl.commercelink.stores.PayoutChannel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pl.commercelink.payments.statements.StatementOrders.*;

@ExtendWith(MockitoExtension.class)
class StatementBookingTest {

    private static final String ORDER = "3f9a2c1e-7b4d-4e1a-9c2f-5d8e6a7b9c01";
    private static final BankOperation TRANSFER = operation("WB-7F3K", "1249.00", "Zamowienie 3f9a2c1e", "JAN KOWALSKI");

    @Mock OrdersRepository ordersRepository;
    @Mock OrderLifecycle orderLifecycle;
    @Mock ReceiptAttemptService receiptAttemptService;
    @InjectMocks StatementBooking booking;

    @Test
    void fillsThePendingPaymentWithTheOperationsNumberAndDate() {
        // given
        Order order = order(ORDER, 1249.00, pending(PaymentSource.BankTransfer));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(outcome.booked()).isTrue();
        assertThat(outcome.orders()).extracting(StatementBooking.OrderOutcome::result).containsExactly(StatementBooking.Result.BOOKED);
        assertThat(order.getPayments()).hasSize(1);
        Payment payment = order.getPayments().get(0);
        assertThat(payment.getAmount()).isEqualTo(1249.00);
        assertThat(payment.getSource()).isEqualTo(PaymentSource.BankTransfer);
        assertThat(payment.getBankTransactionNo()).isEqualTo("WB-7F3K");
        assertThat(payment.getBankTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(payment.getReferenceNo()).isEqualTo("Zamowienie 3f9a2c1e");
        verify(orderLifecycle).update(order);
    }

    @Test
    void addsAPaymentWhenNoneIsPendingAndKeepsTheOthers() {
        // given
        Order order = order(ORDER, 1500.00, booked(251.00, "WB-OLD"));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(order.getPayments()).hasSize(2);
        assertThat(order.getPayments().get(1).getBankTransactionNo()).isEqualTo("WB-7F3K");
        assertThat(order.getUnpaidAmount()).isZero();
    }

    @Test
    void keepsTheReferenceNumberThePaymentAlreadyHas() {
        // given
        Order order = order(ORDER, 1249.00, pending(PaymentSource.BankTransfer, null, "PRO 0391/10/2026", 0));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(order.getPayments().get(0).getReferenceNo()).isEqualTo("PRO 0391/10/2026");
    }

    @Test
    void bookingAPayoutKeepsTheOnlinePaymentAndRecordsTheFeeThePayerKept() {
        // given
        PayoutChannel allegro = new PayoutChannel("ALLEGRO", "Allegro", null, null, true);
        Order order = fromMarketplace(order(ORDER, 649.00, pending(PaymentSource.OnlinePayment, "", "7f2c9e10", 64.90)), "Allegro");
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);
        BankOperation payout = operation("WB-PAY", "584.10", "Wyplata srodkow Allegro", "ALLEGRO SP. Z O.O.");

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", payout, List.of(allocation("649.00", "64.90")), List.of(allegro));

        // then
        assertThat(outcome.booked()).isTrue();
        Payment payment = order.getPayments().get(0);
        assertThat(payment.getSource()).isEqualTo(PaymentSource.OnlinePayment);
        assertThat(payment.getReferenceNo()).isEqualTo("7f2c9e10");
        assertThat(payment.getAmount()).isEqualTo(649.00);
        assertThat(payment.getFee()).isEqualTo(64.90);
        assertThat(payment.getBankTransactionNo()).isEqualTo("WB-PAY");
    }

    @Test
    void aPayerThatDoesNotDeductItsFeeLeavesTheRecordedFeeInPlace() {
        // given
        PayoutChannel gateway = new PayoutChannel("PAYPRO", PayoutChannel.SHOP, PaymentSource.OnlinePayment, "Przelewy24", false);
        Order order = order(ORDER, 300.00, pending(PaymentSource.OnlinePayment, "Przelewy24", "P24-1", 4.50));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        booking.book("store-1", operation("WB-P24", "300.00", "Przelewy24 wyplata", "PAYPRO SA"),
                List.of(allocation("300.00", "0")), List.of(gateway));

        // then
        assertThat(order.getPayments().get(0).getFee()).isEqualTo(4.50);
    }

    @Test
    void aLoanFromTheCallCentreKeepsItsInstallmentsMethod() {
        // given
        PayoutChannel santander = new PayoutChannel("SANTANDER", null, PaymentSource.Installments, null, false);
        Order order = order(ORDER, 500.00, pending(PaymentSource.Installments));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", operation("WB-SAN", "500.00", "Wyplata kredytow", "SANTANDER"),
                List.of(allocation("500.00", "0")), List.of(santander));

        // then
        assertThat(outcome.booked()).isTrue();
        assertThat(order.getPayments().get(0).getSource()).isEqualTo(PaymentSource.Installments);
        assertThat(order.getPayments().get(0).getBankTransactionNo()).isEqualTo("WB-SAN");
        verify(receiptAttemptService, never()).locksOrder(any());
    }

    @Test
    void refusesAllocationsThatDoNotAddUpToTheTransfer() {
        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1000.00", "0")), List.of());

        // then
        assertThat(outcome.refusalKey()).isEqualTo("statement.book.error.sum");
        verify(ordersRepository, never()).findById(any(), any());
    }

    @Test
    void refusesAFeeOnADirectTransferFromTheCustomer() {
        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1259.00", "10.00")), List.of());

        // then
        assertThat(outcome.refusalKey()).isEqualTo("statement.book.error.allocation");
    }

    @Test
    void doesNothingForAnOperationTheOrderAlreadyHolds() {
        // given
        Order order = order(ORDER, 2500.00, booked(1249.00, "wb-7f3k"));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(outcome.booked()).isTrue();
        assertThat(outcome.orders()).extracting(StatementBooking.OrderOutcome::result)
                .containsExactly(StatementBooking.Result.ALREADY_BOOKED);
        assertThat(order.getPayments()).hasSize(1);
        verify(orderLifecycle, never()).update(any());
    }

    @Test
    void refusesAClosedOrder() {
        // given
        Order order = order(ORDER, 1249.00, pending(PaymentSource.BankTransfer));
        order.setStatus(OrderStatus.Cancelled);
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(outcome.booked()).isFalse();
        assertThat(outcome.orders()).extracting(StatementBooking.OrderOutcome::messageKey)
                .containsExactly("statement.book.order.closed");
        verify(orderLifecycle, never()).update(any());
    }

    @Test
    void turnsAFailedOnlinePaymentIntoTheTransferTheCustomerMadeInstead() {
        // given
        Order order = order(ORDER, 1249.00, pending(PaymentSource.OnlinePayment, "Przelewy24", "P24-1877", 5.00));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);
        when(receiptAttemptService.locksOrder(order)).thenReturn(false);

        // when
        booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        Payment payment = order.getPayments().get(0);
        assertThat(payment.getSource()).isEqualTo(PaymentSource.BankTransfer);
        assertThat(payment.getName()).isNull();
        assertThat(payment.getFee()).isZero();
        assertThat(payment.getReferenceNo()).isEqualTo("Zamowienie 3f9a2c1e");
    }

    @Test
    void refusesToChangeAMethodTheEReceiptHasFixed() {
        // given
        Order order = order(ORDER, 1249.00, pending(PaymentSource.OnlinePayment, "Przelewy24", "P24-1877", 5.00));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(order);
        when(receiptAttemptService.locksOrder(order)).thenReturn(true);

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(outcome.orders()).extracting(StatementBooking.OrderOutcome::messageKey)
                .containsExactly("statement.book.order.methodLocked");
        assertThat(order.getPayments().get(0).getSource()).isEqualTo(PaymentSource.OnlinePayment);
        verify(orderLifecycle, never()).update(any());
    }

    @Test
    void readsTheOrderAgainWhenAnotherChangeWonTheRace() {
        // given
        Order stale = order(ORDER, 1249.00, pending(PaymentSource.BankTransfer));
        Order fresh = order(ORDER, 1249.00, pending(PaymentSource.BankTransfer));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(stale, fresh);
        doThrow(new ConditionalCheckFailedException("version")).doNothing().when(orderLifecycle).update(any());

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(outcome.booked()).isTrue();
        verify(orderLifecycle).update(stale);
        verify(orderLifecycle).update(fresh);
        assertThat(fresh.getPayments().get(0).getBankTransactionNo()).isEqualTo("WB-7F3K");
    }

    @Test
    void givesUpAfterRepeatedConflicts() {
        // given
        when(ordersRepository.findById("store-1", ORDER))
                .thenAnswer(invocation -> order(ORDER, 1249.00, pending(PaymentSource.BankTransfer)));
        doThrow(new ConditionalCheckFailedException("version")).when(orderLifecycle).update(any());

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", TRANSFER, List.of(allocation("1249.00", "0")), List.of());

        // then
        assertThat(outcome.orders()).extracting(StatementBooking.OrderOutcome::messageKey)
                .containsExactly("statement.book.order.stale");
        verify(orderLifecycle, times(3)).update(any());
    }

    @Test
    void bookingOneOrderOfAPayoutDoesNotDependOnAnother() {
        // given
        PayoutChannel cod = new PayoutChannel("FURGONETKA", null, PaymentSource.CashOnDelivery, null, false);
        String other = "5d0b8f13-0000-4000-8000-000000000002";
        Order first = order(ORDER, 100.00, pending(PaymentSource.CashOnDelivery));
        when(ordersRepository.findById("store-1", ORDER)).thenReturn(first);
        when(ordersRepository.findById("store-1", other)).thenReturn(null);

        // when
        StatementBooking.Outcome outcome = booking.book("store-1", operation("WB-COD", "150.00", "Pobrania", "FURGONETKA"),
                List.of(new Allocation(ORDER, new BigDecimal("100.00"), BigDecimal.ZERO),
                        new Allocation(other, new BigDecimal("50.00"), BigDecimal.ZERO)), List.of(cod));

        // then
        assertThat(outcome.orders()).extracting(StatementBooking.OrderOutcome::result)
                .containsExactly(StatementBooking.Result.BOOKED, StatementBooking.Result.FAILED);
        assertThat(outcome.booked()).isFalse();
        verify(orderLifecycle).update(first);
    }

    private static Allocation allocation(String amount, String fee) {
        return new Allocation(ORDER, new BigDecimal(amount), new BigDecimal(fee));
    }
}
