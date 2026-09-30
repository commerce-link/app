package pl.commercelink.invoicing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoreActivity;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceCreationEventListenerTest {

    @Mock private InvoicingService invoicingService;
    @Mock private OrdersRepository ordersRepository;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private InvoiceCreationEventListener listener;

    @Test
    void createsInvoiceOfActiveStore() {
        // given
        Order order = new Order();
        when(storeActivity.isActive("store-1")).thenReturn(true);
        when(ordersRepository.findById("store-1", "order-1")).thenReturn(order);
        when(invoicingService.createInvoice(order, DocumentType.InvoiceVat, true))
                .thenReturn(new InvoicingService.OperationResult("inv-1", "FV/1", null, null));

        // when
        listener.handleMessage(new InvoiceCreationRequest("store-1", "order-1", DocumentType.InvoiceVat, true));

        // then
        verify(invoicingService).createInvoice(order, DocumentType.InvoiceVat, true);
    }

    @Test
    void createsNoInvoiceForInactiveStore() {
        // given
        when(storeActivity.isActive("store-1")).thenReturn(false);

        // when
        listener.handleMessage(new InvoiceCreationRequest("store-1", "order-1", DocumentType.InvoiceVat, true));

        // then
        verifyNoInteractions(invoicingService, ordersRepository);
    }
}
