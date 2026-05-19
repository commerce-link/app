package pl.commercelink.invoicing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.invoicing.api.Invoice;
import pl.commercelink.invoicing.api.InvoiceRequest;
import pl.commercelink.invoicing.api.InvoicingProvider;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.OrderEventsRepository;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.email.EmailClient;
import pl.commercelink.stores.InvoicingConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoicingServiceTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;
    @Mock
    private EmailClient emailClient;
    @Mock
    private OrderEventsRepository orderEventsRepository;
    @Mock
    private MessageSource messageSource;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @Mock
    private Store store;
    @Mock
    private InvoicingConfiguration invoicingConfiguration;
    @Mock
    private InvoicingProvider invoicingProvider;

    @InjectMocks
    private InvoicingService invoicingService;

    @BeforeEach
    void setupExecutorPassThrough() {
        when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSave());
        when(optimisticLockingExecutor.modifyAndSaveReturning(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.passThroughModifyAndSaveReturning());
    }

    @Test
    @DisplayName("createInvoice returns billing-missing error when order billing details are not properly filled")
    void createInvoiceReturnsErrorWhenBillingDetailsAreNotProperlyFilled() {
        // given
        Order order = orderBase();
        order.setBillingDetails(BillingDetails._default()); // not properly filled
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getInvoicingConfiguration()).thenReturn(invoicingConfiguration);

        // when
        InvoicingService.OperationResult result = invoicingService.createInvoice(order, DocumentType.InvoiceVat, false);

        // then
        assertThat(result.hasError()).isTrue();
        assertThat(result.getErrorMessage()).contains("Billing details are missing");
        verify(invoicingProviderFactory, never()).get(any());
        verify(ordersRepository, never()).save(any());
        verify(orderLifecycleEventPublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("createInvoice adds invoice document to order and publishes InvoiceCreated lifecycle event when provider succeeds")
    void createInvoiceAddsInvoiceDocumentToOrderAndPublishesLifecycleEventWhenProviderSucceeds() {
        // given
        Order order = orderWithFilledBillingDetails();
        Invoice invoice = new Invoice("inv-1", "FV/1/2026", ORDER_ID, null, "https://example.com/inv/1",
                "PLN", 1.0, false, null, Collections.emptyList(), null, null);
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getInvoicingConfiguration()).thenReturn(invoicingConfiguration);
        when(invoicingConfiguration.isSplitPaymentsEnabled()).thenReturn(false);
        when(invoicingConfiguration.getPaymentTerms()).thenReturn(14);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(invoicingProviderFactory.get(store)).thenReturn(invoicingProvider);
        when(invoicingProvider.createInvoice(any(InvoiceRequest.class))).thenReturn(invoice);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);

        // when
        InvoicingService.OperationResult result = invoicingService.createInvoice(order, DocumentType.InvoiceVat, false);

        // then
        assertThat(result.hasError()).isFalse();
        assertThat(result.getInvoiceId()).isEqualTo("inv-1");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getDocuments())
                .extracting("id").contains("inv-1");

        verify(orderLifecycleEventPublisher).publish(orderCaptor.getValue(), OrderLifecycleEventType.InvoiceCreated);
    }

    @Test
    @DisplayName("createInvoice persists nothing and does not publish event when invoice provider operation returns an error")
    void createInvoicePersistsNothingAndDoesNotPublishWhenInvoicingProviderReturnsError() {
        // given
        // InvoiceAdvance branch checks WMS order number from order documents — when missing, returns error
        Order order = orderWithFilledBillingDetails(); // no WMS Order document attached
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(store.getInvoicingConfiguration()).thenReturn(invoicingConfiguration);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());

        // when
        InvoicingService.OperationResult result = invoicingService.createInvoice(order, DocumentType.InvoiceAdvance, false);

        // then
        assertThat(result.hasError()).isTrue();
        verify(invoicingProvider, never()).createInvoice(any());
        verify(ordersRepository, never()).save(any());
        verify(orderLifecycleEventPublisher, never()).publish(any(), eq(OrderLifecycleEventType.InvoiceCreated));
    }

    private Order orderBase() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        return order;
    }

    private Order orderWithFilledBillingDetails() {
        Order order = orderBase();
        BillingDetails billing = new BillingDetails();
        billing.setName("Jan Kowalski");
        billing.setStreetAndNumber("Marszalkowska 1");
        billing.setPostalCode("00-001");
        billing.setCity("Warszawa");
        billing.setCountry("PL");
        billing.setEmail("jan@example.com");
        billing.setPhone("+48123456789");
        order.setBillingDetails(billing);
        return order;
    }
}
