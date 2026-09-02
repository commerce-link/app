package pl.commercelink.inventory.deliveries;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production trigger for the dropship tracking sweep. An EventBridge schedule puts one trigger message on the
 * queue, so exactly one instance runs the sweep no matter how many are up. The message carries no payload.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class DropshipTrackingSweepListener {

    static final String QUEUE_NAME = "supplier-dropship-tracking-sweep-queue";

    private final DropshipTrackingSweep sweep;

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
