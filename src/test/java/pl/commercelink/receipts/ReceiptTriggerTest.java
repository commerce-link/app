package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptTriggerTest {

    private final ReceiptEligibility eligibility = mock(ReceiptEligibility.class);
    private final ReceiptAttemptService service = mock(ReceiptAttemptService.class);
    private final ReceiptTrigger trigger = new ReceiptTrigger(eligibility, service);

    /** A store with a receipt system chosen, whose orders may have attempts. */
    private static Store receiptStore() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        return store;
    }

    @Test
    void startsAnAttemptForACandidate() {
        // given
        Store store = receiptStore();
        Order order = b2cOrder(100);
        when(eligibility.automaticCandidate(store, order)).thenReturn(true);

        // when
        trigger.onOrderSaved(order, store);

        // then
        verify(service).startAutomatic(store, order);
    }

    @Test
    void neverBreaksTheOrderUpdate() {
        // given
        Store store = receiptStore();
        Order order = b2cOrder(100);
        when(eligibility.automaticCandidate(store, order)).thenThrow(new RuntimeException("dynamo down"));

        // when / then
        assertThatCode(() -> trigger.onOrderSaved(order, store)).doesNotThrowAnyException();
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void ignoresOrdersThatAreNotDelivered() {
        // given
        Order order = b2cOrder(100);
        order.setStatus(OrderStatus.Shipping);

        // when
        trigger.onOrderSaved(order, receiptStore());

        // then
        verifyNoInteractions(eligibility);
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void deliveredUnpaidOrderStartsTheAttemptWithoutWaitingForThePayment() {
        // given
        Store store = receiptStore();
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        ReceiptTrigger realTrigger = new ReceiptTrigger(new ReceiptEligibility(factory), service);
        Order order = order(100.0, payment(PaymentSource.CashOnDelivery, 0.0));

        // when
        realTrigger.onOrderSaved(order, store);

        // then
        verify(service).startAutomatic(store, order);
    }

    @Test
    void orderSavedReconcilesTheDeadAttemptAlerts() {
        // given
        Order order = b2cOrder(100);
        order.addDocument(new Document(null, "KASA/1", null, DocumentType.Receipt, LocalDate.of(2026, 9, 29)));

        // when
        trigger.onOrderSaved(order, receiptStore());

        // then
        verify(service).reconcileDeadAttemptAlerts(order);
    }

    @Test
    void aStoreThatNeverHadAReceiptSystemSkipsTheReconcile() {
        // given: the lifecycle cron saves every Shipping and Delivered order of every store
        Order order = b2cOrder(100);

        // when
        trigger.onOrderSaved(order, new Store());

        // then: no attempt can exist, so no attempts query
        verifyNoInteractions(service);
    }

    @Test
    void aStoreThatDisconnectedItsReceiptSystemStillReconciles() {
        // given: its orders may keep dead attempts and their alerts from before
        Store store = new Store();
        store.getReceiptConfiguration().setDisconnectedAt(LocalDateTime.of(2026, 9, 1, 12, 0));
        Order order = b2cOrder(100);
        order.addDocument(new Document(null, "KASA/1", null, DocumentType.Receipt, LocalDate.of(2026, 9, 29)));

        // when
        trigger.onOrderSaved(order, store);

        // then
        verify(service).reconcileDeadAttemptAlerts(order);
    }

    @Test
    void aStoreDisconnectedBeforeTheMarkerExistedStillReconciles() {
        // given: automatic receipts were on once (enabledAt is never cleared), the system was disconnected before
        // disconnectedAt existed
        Store store = new Store();
        store.getReceiptConfiguration().setEnabledAt(LocalDateTime.of(2026, 9, 28, 12, 0));
        Order order = b2cOrder(100);

        // when
        trigger.onOrderSaved(order, store);

        // then
        verify(service).reconcileDeadAttemptAlerts(order);
    }

    @Test
    void anUnknownStoreStillReconciles() {
        // given
        Order order = b2cOrder(100);

        // when
        trigger.onOrderSaved(order, null);

        // then
        verify(service).reconcileDeadAttemptAlerts(order);
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void anOrderNotYetDeliveredReconcilesTheAlertsWithoutStartingAnAttempt() {
        // given: a manual e-receipt blocked while the sale was still Assembled, then the cash register receipt
        Order order = b2cOrder(100);
        order.setStatus(OrderStatus.Assembled);
        order.addDocument(new Document(null, "KASA/1", null, DocumentType.Receipt, LocalDate.of(2026, 9, 29)));

        // when
        trigger.onOrderSaved(order, receiptStore());

        // then
        verify(service).reconcileDeadAttemptAlerts(order);
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void failingToReconcileAlertsNeverBreaksTheOrderUpdate() {
        // given
        Order order = b2cOrder(100);
        doThrow(new RuntimeException("dynamo down")).when(service).reconcileDeadAttemptAlerts(order);

        // when / then
        assertThatCode(() -> trigger.onOrderSaved(order, receiptStore())).doesNotThrowAnyException();
    }

    @Test
    void anOrderWithItsClosingDocumentSettlesDeadAttempts() {
        // given
        Order order = b2cOrder(100);
        order.setStatus(OrderStatus.Completed);
        order.addDocument(new Document(null, "KASA/1", null, DocumentType.Receipt, LocalDate.of(2026, 9, 29)));

        // when / then
        assertThat(ReceiptTrigger.settlesDeadAttempts(order)).isTrue();
    }

    @Test
    void anOrderWithoutAClosingDocumentDoesNotSettleDeadAttempts() {
        // given
        Order order = b2cOrder(100);

        // when / then
        assertThat(ReceiptTrigger.settlesDeadAttempts(order)).isFalse();
    }

    @Test
    void cancellingResolvesTheDeadAttemptAlerts() {
        // given: the lifecycle cancelled a fully returned order and saved it
        Order order = b2cOrder(100);
        order.setStatus(OrderStatus.Cancelled);

        // when
        trigger.onOrderSaved(order, receiptStore());

        // then: a cancelled order settles its dead attempts, so the reconcile resolves their alerts (the resolving
        // itself: ReceiptAttemptServiceTest#reconcileResolvesDeadAttemptAlertsOfACancelledOrder)
        assertThat(ReceiptTrigger.settlesDeadAttempts(order)).isTrue();
        verify(service).reconcileDeadAttemptAlerts(order);
        verify(service, never()).startAutomatic(any(), any());
    }
}
