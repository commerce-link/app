package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SupplierPurchaseEventPublisher {

    private static final String QUEUE_NAME = "supplier-purchase-queue.fifo";

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(SupplierPurchaseEventRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .messageGroupId(request.getStoreId() + ":" + request.getProvider())
                .messageDeduplicationId(request.getPurchaseRef())
        );
    }
}
