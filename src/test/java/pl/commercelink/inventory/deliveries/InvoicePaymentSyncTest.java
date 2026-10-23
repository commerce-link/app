package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePaymentSyncTest {

    @Test
    void paidInvoiceAddsAPaymentToADeliveryWithoutOne() {
        // when / then
        assertThat(InvoicePaymentSync.of(true, false)).isEqualTo(InvoicePaymentSync.ADD);
    }

    @Test
    void unpaidInvoiceRemovesThePaymentsOfADeliveryThatHasThem() {
        // when / then
        assertThat(InvoicePaymentSync.of(false, true)).isEqualTo(InvoicePaymentSync.REMOVE);
    }

    @Test
    void paymentsStayWhenInvoiceAndDeliveryAgree() {
        // when / then
        assertThat(InvoicePaymentSync.of(true, true)).isEqualTo(InvoicePaymentSync.NONE);
        assertThat(InvoicePaymentSync.of(false, false)).isEqualTo(InvoicePaymentSync.NONE);
    }
}
