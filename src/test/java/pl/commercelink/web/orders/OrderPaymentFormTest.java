package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentDirection;
import pl.commercelink.orders.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPaymentFormTest {

    private static OrderPaymentForm posted(boolean pending, String amount, String fee, String date) {
        return new OrderPaymentForm("o-1", 1, "v", pending, PaymentSource.BankTransfer, " Jan ", amount, fee, "",
                " OP-1 ", date, null, null);
    }

    @Test
    void anAmountIsTypedWithACommaOrADotAndSpaces() {
        // then
        assertThat(OrderPaymentForm.parseAmount("149,99")).isEqualByComparingTo("149.99");
        assertThat(OrderPaymentForm.parseAmount(" 1 499.50 ")).isEqualByComparingTo("1499.50");
        assertThat(OrderPaymentForm.parseAmount("-100")).isEqualByComparingTo("-100");
        assertThat(OrderPaymentForm.parseAmount("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(OrderPaymentForm.parseAmount("sto")).isNull();
        assertThat(OrderPaymentForm.parseAmount("1,2,3")).isNull();
    }

    @Test
    void aSettledPaymentNeedsAnAmountOtherThanZeroAndThePendingOneMayKeepZero() {
        // then
        assertThat(posted(false, "0", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount.zero");
        assertThat(posted(false, "", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount.zero");
        assertThat(posted(false, "abc", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount");
        assertThat(posted(true, "0", null, null).validate()).isEmpty();
        assertThat(posted(false, "-20", null, null).validate()).isEmpty();
    }

    @Test
    void aFeeIsZeroOrMoreAndADateComesFromTheCalendar() {
        // then
        assertThat(posted(false, "10", "-1", null).validate()).containsEntry("payment-1-fee", "order.payments.error.fee");
        assertThat(posted(false, "10", "x", null).validate()).containsEntry("payment-1-fee", "order.payments.error.fee");
        assertThat(posted(false, "10", "20.09.2026", "20.09.2026").validate())
                .containsEntry("payment-1-bankTransactionDate", "order.payments.error.date");
        assertThat(posted(false, "10", "0", "2026-09-20").validate()).isEmpty();
    }

    @Test
    void aPaymentWithoutAMethodIsRefused() {
        // given
        OrderPaymentForm form = new OrderPaymentForm("o-1", 0, "v", true, null, null, "0", null, null, null, null,
                null, null);

        // then
        assertThat(form.validate()).containsEntry("payment-0-source", "order.payments.error.source");
    }

    @Test
    void thePostedPaymentKeepsTheSavedDirectionAndStoresBlankTextAsNothing() {
        // given
        Payment saved = new Payment("ZW/1", "Jan", PaymentSource.BankTransfer, PaymentDirection.Outgoing, -100, 0, null, null);

        // when
        Payment payment = posted(false, "-120,5", "1.25", "2026-09-20").toPayment(saved);

        // then
        assertThat(payment.getDirection()).isEqualTo(PaymentDirection.Outgoing);
        assertThat(payment.getAmount()).isEqualTo(-120.5);
        assertThat(payment.getFee()).isEqualTo(1.25);
        assertThat(payment.getName()).isEqualTo("Jan");
        assertThat(payment.getReferenceNo()).isNull();
        assertThat(payment.getBankTransactionNo()).isEqualTo("OP-1");
        assertThat(payment.getBankTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    void theFormShowsTheSavedPaymentAndItsVersionFollowsEveryShownField() {
        // given
        Payment payment = new Payment("REF-1", "Jan", PaymentSource.Card, 500, 2.5);
        payment.setBankTransactionDate(LocalDate.of(2026, 9, 20));

        // when
        OrderPaymentForm form = OrderPaymentForm.of("o-1", 2, payment);
        String before = OrderPaymentForm.version(payment);
        payment.setBankTransactionNo("OP-2");

        // then
        assertThat(form.amount()).isEqualTo("500.00");
        assertThat(form.fee()).isEqualTo("2.50");
        assertThat(form.bankTransactionDate()).isEqualTo("2026-09-20");
        assertThat(form.number()).isEqualTo(3);
        assertThat(form.dialogId()).isEqualTo("payment-dialog-2");
        assertThat(form.field("amount")).isEqualTo("payment-2-amount");
        assertThat(form.pending()).isFalse();
        assertThat(form.version()).isEqualTo(before);
        assertThat(OrderPaymentForm.version(payment)).isNotEqualTo(before);
    }

    @Test
    void anUntouchedFormSavesThePaymentAsItWas() {
        // given: a blank text and no text are the same payment, and a fee of 0 shows as an empty field
        Payment saved = new Payment("REF-1", "", PaymentSource.BankTransfer, PaymentDirection.Incoming, 100.1, 0, null, null);
        OrderPaymentForm form = OrderPaymentForm.of("o-1", 0, saved);

        // when
        Payment resaved = form.toPayment(saved);

        // then
        assertThat(form.fee()).isNull();
        assertThat(form.amount()).isEqualTo("100.10");
        assertThat(form.validate()).isEmpty();
        assertThat(OrderPaymentForm.version(resaved)).isEqualTo(OrderPaymentForm.version(saved));
    }

    @Test
    void amountsAreValidatedAndStoredToTheGrosz() {
        // given
        Payment saved = Payment.bankTransfer("REF-1", "Jan", 100);

        // when
        Payment rounded = posted(false, "100.125", "0.005", null).toPayment(saved);

        // then
        assertThat(rounded.getAmount()).isEqualTo(100.13);
        assertThat(rounded.getFee()).isEqualTo(0.01);
        assertThat(OrderPaymentForm.of("o-1", 0, new Payment("R", "J", PaymentSource.Card, 100.125, 0)).amount())
                .isEqualTo("100.13");
    }

    @Test
    void anAmountThatRoundsToZeroIsZeroForASettledPayment() {
        // then
        assertThat(posted(false, "0.001", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount.zero");
        assertThat(posted(false, "1e-400", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount.zero");
        assertThat(posted(false, "1e-999999999", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount.zero");
        assertThat(posted(true, "0.004", null, null).validate()).isEmpty();
        assertThat(posted(false, "0.005", null, null).validate()).isEmpty();
    }

    @Test
    void anAmountOrFeeOutOfRangeIsAFieldErrorNotAFailure() {
        // then
        assertThat(posted(false, "1e400", "1e999999999", null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.range")
                .containsEntry("payment-1-fee", "order.payments.error.range");
        assertThat(posted(false, "-10000000", "10000000", null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.range")
                .containsEntry("payment-1-fee", "order.payments.error.range");
        assertThat(posted(false, "9999999.99", "9999999.99", null).validate()).isEmpty();
        assertThat(posted(false, "1e99999999999", null, null).validate())
                .containsEntry("payment-1-amount", "order.payments.error.amount");
    }

    @Test
    void thePendingPaymentShowsAsPending() {
        // when
        OrderPaymentForm form = OrderPaymentForm.of("o-1", 0, new Payment(PaymentSource.CashOnDelivery));

        // then
        assertThat(form.pending()).isTrue();
        assertThat(form.amount()).isEqualTo("0.00");
        assertThat(form.source()).isEqualTo(PaymentSource.CashOnDelivery);
    }
}
