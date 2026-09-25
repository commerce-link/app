package pl.commercelink.taxonomy;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production trigger for the category match sweep. An EventBridge schedule puts one trigger message on the
 * queue per tick, so exactly one instance submits category match requests no matter how many are up.
 * The message carries no payload.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class TaxonomyCategoryMatchSweepListener {

    static final String QUEUE_NAME = "taxonomy-category-match-sweep-queue";

    private final TaxonomyCategoryMatchSweep sweep;

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
