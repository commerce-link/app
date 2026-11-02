package pl.commercelink.marketplace.links;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Orders an offer matching run: a store has thousands of offers over many pages of the marketplace API, more than a web
 * request may take. The result shows up on the connection's page once the worker has saved it; a run the marketplace
 * slows down goes on in continuation messages.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceOfferMatchPublisher {

    static final String QUEUE_NAME = "marketplace-offer-match-queue";
    /** The longest delay SQS accepts on a message. */
    private static final int MAX_DELAY_SECONDS = 900;

    private final SqsTemplate sqsTemplate;

    public void order(String storeId, String marketplace) {
        sqsTemplate.send(QUEUE_NAME, MarketplaceOfferMatchMessage.start(storeId, marketplace).toJson());
    }

    /** The next part of a run, held back by SQS for the delay instead of a worker sleeping through it. */
    void continueRun(String storeId, String marketplace, String runId, String cursor, Duration delay) {
        String body = MarketplaceOfferMatchMessage.continuation(storeId, marketplace, runId, cursor).toJson();
        sqsTemplate.send(to -> to
                .queue(QUEUE_NAME)
                .payload(body)
                .delaySeconds(delaySeconds(delay)));
    }

    private static int delaySeconds(Duration delay) {
        long millis = Math.max(0, delay.toMillis());
        return (int) Math.min(MAX_DELAY_SECONDS, (millis + 999) / 1000);
    }
}
