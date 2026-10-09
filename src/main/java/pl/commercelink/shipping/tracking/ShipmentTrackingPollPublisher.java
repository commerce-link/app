package pl.commercelink.shipping.tracking;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentTrackingPollPublisher {

    static final String QUEUE_NAME = "shipment-tracking-poll-queue";

    private final SqsTemplate sqsTemplate;

    public void publish(ShipmentTrackingPollRequest request) {
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(request));
    }
}
