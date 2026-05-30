package pl.commercelink.invoicing;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;

@Component
public class InvoiceCreationEventPublisher {

    private static final String QUEUE_NAME = "order-invoicing-queue.fifo";

    @Value("${application.env}")
    private String env;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(Order order, boolean sendEmail) {
        order.getNextInvoiceToIssue().ifPresent(type -> publish(order, type, sendEmail));
    }

    public void publish(Order order, DocumentType documentType, boolean sendEmail) {
        if (!"prod".equals(env)) {
            return;
        }

        InvoiceCreationRequest request = new InvoiceCreationRequest(
                order.getStoreId(),
                order.getOrderId(),
                documentType,
                sendEmail
        );

        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .messageGroupId(order.getStoreId())
                .messageDeduplicationId(order.getOrderId() + ":" + documentType.name())
        );
    }
}
