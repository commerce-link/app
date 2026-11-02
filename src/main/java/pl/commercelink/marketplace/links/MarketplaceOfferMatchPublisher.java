package pl.commercelink.marketplace.links;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Orders an offer matching run: a store has thousands of offers over many pages of the marketplace API, more than a web
 * request may take. The result shows up on the connection's page once the worker has saved it.
 */
@Component
@RequiredArgsConstructor
public class MarketplaceOfferMatchPublisher {

    static final String QUEUE_NAME = "marketplace-offer-match-queue";

    private final SqsTemplate sqsTemplate;

    public void order(String storeId, String marketplace) {
        sqsTemplate.send(QUEUE_NAME, new MarketplaceOfferMatchMessage(storeId, marketplace).toJson());
    }
}
