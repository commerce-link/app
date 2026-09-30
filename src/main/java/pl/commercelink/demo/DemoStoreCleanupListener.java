package pl.commercelink.demo;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production trigger for the expired demo store cleanup. An EventBridge schedule puts one trigger message on the
 * queue every hour, so exactly one instance deletes the expired stores no matter how many are up. The message
 * carries no payload. Like the cleanup itself, it exists only with demo registration on.
 */
@Component
@ConditionalOnProperty(name = "app.registration.demo", havingValue = "true")
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class DemoStoreCleanupListener {

    static final String QUEUE_NAME = "demo-store-cleanup-queue";

    private final DemoStoreCleanup cleanup;

    @SqsListener(
            value = QUEUE_NAME,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    void handleMessage(String message) {
        cleanup.deleteExpiredDemoStores();
    }
}
