package pl.commercelink.shipping;

import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentTrackingEventListener {

    private final ShipmentTrackingSubscriber subscriber;

    @SqsListener(
            value = "shipment-tracking-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(ShipmentTrackingCheckRequest payload,
            @Header(SqsHeaders.MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT) String receiveCount) {
        subscriber.check(payload, Integer.parseInt(receiveCount));
    }
}
