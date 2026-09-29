package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptTriggerTest {

    private final StoresRepository stores = mock(StoresRepository.class);
    private final ReceiptEligibility eligibility = mock(ReceiptEligibility.class);
    private final ReceiptAttemptService service = mock(ReceiptAttemptService.class);
    private final ReceiptTrigger trigger = new ReceiptTrigger(stores, eligibility, service);

    @Test
    void startsAnAttemptForACandidate() {
        Store store = new Store();
        Order order = b2cOrder(100);
        when(stores.findById(STORE_ID)).thenReturn(store);
        when(eligibility.automaticCandidate(store, order)).thenReturn(true);

        trigger.onOrderSaved(order);

        verify(service).startAutomatic(store, order);
    }

    @Test
    void neverBreaksTheOrderUpdate() {
        Order order = b2cOrder(100);
        when(stores.findById(STORE_ID)).thenThrow(new RuntimeException("dynamo down"));

        trigger.onOrderSaved(order);

        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void ignoresOrdersThatAreNotDelivered() {
        Order order = b2cOrder(100);
        order.setStatus(pl.commercelink.orders.OrderStatus.Shipping);

        trigger.onOrderSaved(order);

        verifyNoInteractions(stores);
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void deliveredUnpaidOrderStartsTheAttemptWithoutWaitingForThePayment() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        ReceiptTrigger realTrigger = new ReceiptTrigger(stores, new ReceiptEligibility(factory), service);
        when(stores.findById(STORE_ID)).thenReturn(store);
        Order order = order(100.0, payment(PaymentSource.CashOnDelivery, 0.0));

        realTrigger.onOrderSaved(order);

        verify(service).startAutomatic(store, order);
    }

    @Test
    void orderSavedReconcilesTheDeadAttemptAlerts() {
        // given
        Order order = b2cOrder(100);
        order.addDocument(new Document(null, "KASA/1", null, DocumentType.Receipt, LocalDate.of(2026, 9, 29)));
        when(stores.findById(STORE_ID)).thenReturn(new Store());

        // when
        trigger.onOrderSaved(order);

        // then
        verify(service).reconcileDeadAttemptAlerts(order);
    }

    @Test
    void anOrderNotYetDeliveredReconcilesTheAlertsWithoutStartingAnAttempt() {
        // given: a manual e-receipt blocked while the sale was still Assembled, then the cash register receipt
        Order order = b2cOrder(100);
        order.setStatus(OrderStatus.Assembled);
        order.addDocument(new Document(null, "KASA/1", null, DocumentType.Receipt, LocalDate.of(2026, 9, 29)));

        // when
        trigger.onOrderSaved(order);

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
        assertThatCode(() -> trigger.onOrderSaved(order)).doesNotThrowAnyException();
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
}
