package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderIdRefreshEventPublisher {

    static final int INITIAL_DELAY_SECONDS = 300;
    private static final String QUEUE_NAME = "supplier-order-refresh-queue";

    @Autowired
    private SqsTemplate sqsTemplate;

    public void publish(OrderIdRefreshEventRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(INITIAL_DELAY_SECONDS)
        );
    }
}
