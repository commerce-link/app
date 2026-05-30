package pl.commercelink.invoicing;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class InvoiceCreationEventListener {

    @Autowired
    private InvoicingService invoicingService;

    @Autowired
    private OrdersRepository ordersRepository;

    @SqsListener(
            value = "order-invoicing-queue.fifo",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(InvoiceCreationRequest payload) {
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
