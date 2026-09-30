package pl.commercelink.invoicing;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoreActivity;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceCreationEventListener {

    private final InvoicingService invoicingService;
    private final OrdersRepository ordersRepository;
    private final StoreActivity storeActivity;

    @SqsListener(
            value = "order-invoicing-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(InvoiceCreationRequest payload) {
        if (!storeActivity.isActive(payload.getStoreId())) {
            log.warn("Invoice of order {} skipped: store {} is inactive", payload.getOrderId(), payload.getStoreId());
            return;
        }
        Order order = ordersRepository.findById(payload.getStoreId(), payload.getOrderId());
        if (order == null) {
            return;
        }

        DocumentType documentType = payload.getDocumentType();
        boolean sendEmail = payload.isSendEmail() && documentType != DocumentType.Order;

        InvoicingService.OperationResult result = invoicingService.createInvoice(order, documentType, sendEmail);

        if (result.hasError()) {
            throw new RuntimeException("Failed to create invoice for order " + order.getOrderId() + ": " + result.getErrorMessage());
        }
    }
}
