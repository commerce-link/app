package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DropshipTrackingEventPublisher {

    static final String QUEUE_NAME = "supplier-order-tracking-queue";

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(DropshipTrackingEventRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
        );
    }
}
