package pl.commercelink.shipping;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentCancellationEventPublisher {

    static final String QUEUE_NAME = "shipment-cancellation-queue";
    // Furgonetka settles a cancel command within a few seconds (sandbox, 2026-09-29)
    static final int CHECK_DELAY_SECONDS = 10;

    private final SqsTemplate sqsTemplate;

    public void publish(ShipmentCancellationCheckRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(CHECK_DELAY_SECONDS));
    }
}
