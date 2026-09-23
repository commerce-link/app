package pl.commercelink.receipts;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production trigger: an EventBridge schedule (rate 1 minute) puts one message on the queue, so exactly one instance
 * sweeps. The message carries no payload.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class ReceiptSweepListener {

    static final String QUEUE_NAME = "receipt-sweep-queue";

    private final ReceiptSweep sweep;

    @SqsListener(value = QUEUE_NAME, maxConcurrentMessages = "1", maxMessagesPerPoll = "1", pollTimeoutSeconds = "20")
    void handleMessage(String message) {
        sweep.sweep();
    }
}
