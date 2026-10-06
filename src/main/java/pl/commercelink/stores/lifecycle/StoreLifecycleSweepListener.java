package pl.commercelink.stores.lifecycle;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production trigger for the store lifecycle sweep. An EventBridge schedule puts one trigger message on the queue
 * every hour, so exactly one instance runs the sweep no matter how many are up. The message carries no payload.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class StoreLifecycleSweepListener {

    static final String QUEUE_NAME = "store-lifecycle-sweep-queue";

    private final StoreLifecycleSweep sweep;

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
