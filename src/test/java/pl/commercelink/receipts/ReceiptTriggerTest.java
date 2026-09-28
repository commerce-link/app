package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

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

        verifyNoInteractions(stores, service);
    }

    @Test
    void deliveredUnpaidOrderStartsTheAttemptOnceThePaymentIsSaved() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        ReceiptTrigger realTrigger = new ReceiptTrigger(stores, new ReceiptEligibility(factory), service);
        when(stores.findById(STORE_ID)).thenReturn(store);
        Order order = deliveredOrder(100.0);

        realTrigger.onOrderSaved(order);
        verify(service, never()).startAutomatic(any(), any());

        order.getPayments().add(payment(PaymentSource.BankTransfer, 100.0));
        realTrigger.onOrderSaved(order);
        verify(service).startAutomatic(store, order);
    }
}
