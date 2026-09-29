package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class ReceiptTriggerTest {

    private final StoresRepository stores = mock(StoresRepository.class);
    private final ReceiptEligibility eligibility = mock(ReceiptEligibility.class);
    private final ReceiptAttemptService service = mock(ReceiptAttemptService.class);
    private final ReceiptAlerts alerts = mock(ReceiptAlerts.class);
    private final ReceiptTrigger trigger = new ReceiptTrigger(stores, eligibility, service, alerts);

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

        verifyNoInteractions(stores, service);
    }

    @Test
    void deliveredUnpaidOrderStartsTheAttemptWithoutWaitingForThePayment() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        ReceiptTrigger realTrigger = new ReceiptTrigger(stores, new ReceiptEligibility(factory), service, alerts);
        when(stores.findById(STORE_ID)).thenReturn(store);
        Order order = order(100.0, payment(PaymentSource.CashOnDelivery, 0.0));

        realTrigger.onOrderSaved(order);

        verify(service).startAutomatic(store, order);
    }

    @Test
    void raisesAnAlertInsteadOfAnAttemptForAPosOrderWithoutDecision() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        Order order = posOrder(100);
        when(stores.findById(STORE_ID)).thenReturn(store);
        when(eligibility.posDecisionMissing(store, order)).thenReturn(true);
        when(service.attemptsOf(STORE_ID, ORDER_ID)).thenReturn(List.of());

        // when
        trigger.onOrderSaved(order);

        // then
        verify(alerts).raisePosDecision(STORE_ID, ORDER_ID);
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void resolvesThePosAlertOnceTheOrderHasAReceiptOrIsCancelled() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(stores.findById(STORE_ID)).thenReturn(store);
        Order withReceipt = posOrder(100);
        Order cancelled = posOrder(100);
        cancelled.setStatus(OrderStatus.Cancelled);

        // when
        trigger.onOrderSaved(withReceipt);
        trigger.onOrderSaved(cancelled);

        // then
        verify(alerts, times(2)).resolvePosDecision(STORE_ID, ORDER_ID);
        verify(alerts, never()).raisePosDecision(any(), any());
    }

    @Test
    void resolvesThePosAlertWhenARecordedReceiptCompletesThePaidSale() {
        // given: "Dodaj → Paragon" on a delivered, paid POS order makes the lifecycle complete it before the trigger
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(stores.findById(STORE_ID)).thenReturn(store);
        Order completed = posOrder(100);
        completed.setStatus(OrderStatus.Completed);

        trigger.onOrderSaved(completed);

        verify(alerts).resolvePosDecision(STORE_ID, ORDER_ID);
        verify(service, never()).startAutomatic(any(), any());
    }

    @Test
    void noAlertWhenAnAttemptAlreadyExists() {
        // given
        Store store = new Store();
        store.setStoreId(STORE_ID);
        Order order = posOrder(100);
        when(stores.findById(STORE_ID)).thenReturn(store);
        when(eligibility.posDecisionMissing(store, order)).thenReturn(true);
        when(service.attemptsOf(STORE_ID, ORDER_ID)).thenReturn(List.of(new ReceiptAttempt()));

        // when
        trigger.onOrderSaved(order);

        // then
        verify(alerts, never()).raisePosDecision(any(), any());
    }

    @Test
    void ignoresPosOrdersStillInProgress() {
        // given
        Order order = posOrder(100);
        order.setStatus(OrderStatus.New);

        // when
        trigger.onOrderSaved(order);

        // then
        verifyNoInteractions(stores, alerts);
    }
}
