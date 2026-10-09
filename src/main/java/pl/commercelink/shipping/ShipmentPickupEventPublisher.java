package pl.commercelink.shipping;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentPickupEventPublisher {

    static final String QUEUE_NAME = "shipment-pickup-queue";
    private static final int[] DELAYS = {3, 5, 10, 15, 30, 60};

    private final SqsTemplate sqsTemplate;

    public void publish(ShipmentPickupCheckRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(delayFor(request.getAttempt())));
    }

    static int delayFor(int attempt) {
        return DELAYS[Math.min(Math.max(attempt, 1), DELAYS.length) - 1];
    }
}
