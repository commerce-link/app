package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptEligibilityTest {

    private final ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
    private final ReceiptEligibility eligibility = new ReceiptEligibility(factory);

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        return store;
    }

    @Test
    void deliveredConsumerOrderSinceEnablingQualifies() {
        assertThat(eligibility.automaticCandidate(store(), b2cOrder(100))).isTrue();
    }

    @Test
    void ordersDeliveredBeforeEnablingDoNotQualify() {
        Store store = store();
        store.getReceiptConfiguration().disable();
        store.getReceiptConfiguration().enable(DELIVERED_AT.plusMinutes(1));

        assertThat(eligibility.automaticCandidate(store, b2cOrder(100))).isFalse();
    }

    @Test
    void companyOrdersWithTaxIdDoNotQualify() {
        Order order = b2cOrder(100);
        order.getBillingDetails().setTaxId("5250000000");

        assertThat(eligibility.automaticCandidate(store(), order)).isFalse();
    }

    @Test
    void undeliveredZeroValueAndRmaDoNotQualify() {
        Order shipping = b2cOrder(100);
        shipping.setStatus(OrderStatus.Shipping);
        Order free = b2cOrder(0);
        Order rma = b2cOrder(100);
        rma.setSource(new OrderSource("RMA", OrderSourceType.Other));

        Store store = store();
        assertThat(eligibility.automaticCandidate(store, shipping)).isFalse();
        assertThat(eligibility.automaticCandidate(store, free)).isFalse();
        assertThat(eligibility.automaticCandidate(store, rma)).isFalse();
    }

    @Test
    void pointOfSaleOrdersQualifyLikeAnyOtherSource() {
        Order pos = b2cOrder(100);
        pos.setSource(new OrderSource("kasa", OrderSourceType.PointOfSale));

        assertThat(eligibility.automaticCandidate(store(), pos)).isTrue();
    }

    @Test
    void ordersWithoutASourceStillQualify() {
        Order order = b2cOrder(100);
        order.setSource(null);

        assertThat(eligibility.automaticCandidate(store(), order)).isTrue();
    }

    @Test
    void orderWithAClosingDocumentDoesNotQualify() {
        Order order = b2cOrder(100);
        order.addDocument(new pl.commercelink.documents.Document("x", "FV/1", null,
                pl.commercelink.documents.DocumentType.InvoicePersonal));

        assertThat(eligibility.automaticCandidate(store(), order)).isFalse();
    }

    @Test
    void orderWithZeroPaymentDoesNotQualify() {
        assertThat(eligibility.orderQualifies(order(100.0, payment(PaymentSource.BankTransfer, 0.0)))).isFalse();
    }

    @Test
    void partiallyPaidOrderDoesNotQualify() {
        assertThat(eligibility.orderQualifies(order(100.0, payment(PaymentSource.BankTransfer, 60.0)))).isFalse();
    }

    @Test
    void floatingPointFullPaymentQualifies() {
        assertThat(eligibility.orderQualifies(
                order(0.3, payment(PaymentSource.Card, 0.1), payment(PaymentSource.Card, 0.2)))).isTrue();
    }

    @Test
    void overpaidOrderQualifies() {
        assertThat(eligibility.orderQualifies(order(100.0, payment(PaymentSource.Card, 100.5)))).isTrue();
    }

    @Test
    void storeWithoutProviderOrSwitchedOffIsNotReady() {
        Store off = store();
        off.getReceiptConfiguration().disable();
        Store noProvider = store();
        noProvider.removeIntegration(IntegrationType.RECEIPT_PROVIDER);

        assertThat(eligibility.storeReady(off)).isFalse();
        assertThat(eligibility.storeReady(noProvider)).isFalse();
    }
}
