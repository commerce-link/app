package pl.commercelink.invoicing;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;

import java.util.Optional;

@Component
public class InvoiceCreationEventPublisher {

    private static final String QUEUE_NAME = "order-invoicing-queue.fifo";

    @Value("${application.env}")
    private String env;

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(Order order, boolean sendEmail) {
        if (!"prod".equals(env)) {
            return;
        }

        Optional<DocumentType> op = order.getNextInvoiceToIssue();
        if (!op.isPresent()) {
            return;
        }

        InvoiceCreationRequest request = new InvoiceCreationRequest(
                order.getStoreId(),
                order.getOrderId(),
                sendEmail
        );

        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .messageGroupId(order.getStoreId())
                .messageDeduplicationId(order.getOrderId())
        );
    }
}
