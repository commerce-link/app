package pl.commercelink.shipping.tracking;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production trigger for the shipment tracking sweep: an hourly EventBridge schedule puts one payload-less message
 * on the queue, so exactly one instance runs the sweep. The queue never retries (max receive count 1).
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class ShipmentTrackingSweepListener {

    static final String QUEUE_NAME = "shipment-tracking-sweep-queue";

    private final ShipmentTrackingSweep sweep;

    @SqsListener(
            value = QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(String message) {
        sweep.sweep();
    }
}
