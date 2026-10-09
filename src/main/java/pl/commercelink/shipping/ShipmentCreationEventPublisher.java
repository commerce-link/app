package pl.commercelink.shipping;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentCreationEventPublisher {

    static final String QUEUE_NAME = "shipment-creation-queue";
    // Furgonetka settles an order command within seconds (sandbox 2026-10-06); later checks wait longer
    private static final int[] DELAYS = {3, 5, 10, 15, 30, 30, 60, 60};

    private final SqsTemplate sqsTemplate;

    public void publish(ShipmentCreationCheckRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(delayFor(request.getAttempt())));
    }

    static int delayFor(int attempt) {
        return DELAYS[Math.min(Math.max(attempt, 1), DELAYS.length) - 1];
    }
}
