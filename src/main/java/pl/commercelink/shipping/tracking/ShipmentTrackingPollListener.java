package pl.commercelink.shipping.tracking;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** One parcel at a time, so an hourly burst of polls does not hit Allegro in parallel. */
@Component
@RequiredArgsConstructor
class ShipmentTrackingPollListener {

    private final ShipmentTrackingPoller poller;

    @SqsListener(
            value = ShipmentTrackingPollPublisher.QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(ShipmentTrackingPollRequest payload) {
        poller.poll(payload);
    }
}
