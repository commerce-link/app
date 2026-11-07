package pl.commercelink.marketplace.offermatching;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS trigger for the nightly offer matching sweep: an EventBridge schedule puts one payload-less message on the queue
 * every evening, so exactly one instance runs the sweep. The queue never retries (max receive count 1). No local
 * trigger: a local run matches only when ordered by hand.
 */
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
class MarketplaceOfferMatchSweepListener {

    static final String QUEUE_NAME = "marketplace-offer-match-sweep-queue";

    private final MarketplaceOfferMatchSweep sweep;

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
