package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SupplierPurchaseCompletionEventPublisher {

    private static final String QUEUE_NAME = "supplier-purchase-completion-queue";

    @Autowired
    private SqsTemplate sqsTemplate;

    @Value("${supplier.purchase.completion.delay-seconds:60}")
    private int delaySeconds;

    public void publish(SupplierPurchaseCompletionEventRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(delaySeconds)
        );
    }
}
