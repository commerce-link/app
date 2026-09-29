package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.PosReceiptMode;
import pl.commercelink.stores.Store;

import java.time.LocalDate;

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
    void pointOfSaleOrdersQualifyOnceTheOperatorChoseAnEReceipt() {
        Order pos = b2cOrder(100);
        pos.setSource(new OrderSource("kasa", OrderSourceType.PointOfSale));
        Store store = store();

        assertThat(eligibility.automaticCandidate(store, pos)).isFalse();
        pos.setPosEReceiptRequested(true);
        assertThat(eligibility.automaticCandidate(store, pos)).isTrue();
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
    void unpaidOrderQualifies() {
        assertThat(eligibility.orderQualifies(order(100.0, payment(PaymentSource.CashOnDelivery, 0.0)))).isTrue();
    }

    @Test
    void partiallyPaidOrderQualifies() {
        assertThat(eligibility.orderQualifies(order(100.0, payment(PaymentSource.BankTransfer, 60.0)))).isTrue();
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

    private Store storeWithReceipts(PosReceiptMode mode) {
        return withPosMode(store(), mode);
    }

    @Test
    void posOrderWithTheStoresEmailIsNoAutomaticCandidateInAnyMode() {
        for (PosReceiptMode mode : PosReceiptMode.values()) {
            Store store = storeWithReceipts(mode);
            Order order = posOrder(100);

            assertThat(eligibility.automaticCandidate(store, order)).as(mode.name()).isFalse();
            assertThat(eligibility.posDecisionMissing(store, order)).as(mode.name()).isTrue();
        }
    }

    @Test
    void posOrderWithCustomerEmailIsACandidateInEReceiptMode() {
        Store store = storeWithReceipts(PosReceiptMode.E_RECEIPT);
        Order order = posOrder(100);
        order.getBillingDetails().setEmail("klient@example.com");

        assertThat(eligibility.automaticCandidate(store, order)).isTrue();
        assertThat(eligibility.posDecisionMissing(store, order)).isFalse();
    }

    @Test
    void askModeNeedsTheOperatorsChoiceNotJustAnEmail() {
        Store store = storeWithReceipts(PosReceiptMode.ASK);
        Order order = posOrder(100);
        order.getBillingDetails().setEmail("klient@example.com");

        assertThat(eligibility.automaticCandidate(store, order)).isFalse();
        order.setPosEReceiptRequested(true);
        assertThat(eligibility.automaticCandidate(store, order)).isTrue();
    }

    @Test
    void cashRegisterModeNeverIssuesAnEReceiptForPos() {
        Store store = storeWithReceipts(PosReceiptMode.CASH_REGISTER);
        Order order = posOrder(100);
        order.getBillingDetails().setEmail("klient@example.com");
        order.setPosEReceiptRequested(true);

        assertThat(eligibility.automaticCandidate(store, order)).isFalse();
        assertThat(eligibility.posDecisionMissing(store, order)).isTrue();
    }

    @Test
    void aRecordedReceiptSettlesThePosDecision() {
        Store store = storeWithReceipts(PosReceiptMode.CASH_REGISTER);
        Order order = posOrder(100);
        order.addDocument(new Document(null, "123/2026", null, DocumentType.Receipt, LocalDate.of(2026, 9, 23)));

        assertThat(eligibility.posDecisionMissing(store, order)).isFalse();
        assertThat(eligibility.automaticCandidate(store, order)).isFalse();
    }

    @Test
    void storeWithoutPosModeAsksAndSoAlertsForTheStoresEmail() {
        Store store = storeWithReceipts(null);

        assertThat(eligibility.posDecisionMissing(store, posOrder(100))).isTrue();
    }

    @Test
    void noPosDecisionIsMissingWhileReceiptsAreOffOrTheOrderIsUndelivered() {
        Store off = storeWithReceipts(PosReceiptMode.ASK);
        off.getReceiptConfiguration().disable();
        Order undelivered = posOrder(100);
        undelivered.setStatus(OrderStatus.Shipping);

        assertThat(eligibility.posDecisionMissing(off, posOrder(100))).isFalse();
        assertThat(eligibility.posDecisionMissing(storeWithReceipts(PosReceiptMode.ASK), undelivered)).isFalse();
    }

    @Test
    void nonPosOrdersAreUnchanged() {
        Store store = storeWithReceipts(PosReceiptMode.CASH_REGISTER);
        Order order = deliveredOrder(100);

        assertThat(eligibility.automaticCandidate(store, order)).isTrue();
        assertThat(eligibility.posDecisionMissing(store, order)).isFalse();
    }
}
