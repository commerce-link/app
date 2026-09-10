package pl.commercelink.shipping;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentTrackingEventPublisher {

    static final String QUEUE_NAME = "shipment-tracking-queue";
    static final int CHECK_DELAY_SECONDS = 60;

    private final SqsTemplate sqsTemplate;

    public void publish(ShipmentTrackingCheckRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request)
                .delaySeconds(CHECK_DELAY_SECONDS));
    }
}
