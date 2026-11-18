package pl.commercelink.inventory;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Asks for a rebuild of a store's inventory snapshot. The rebuild runs in {@link StoreInventoryRefreshListener}, not
 * in the caller, so a feed import ends with its file stored, and a snapshot can be rebuilt without downloading any
 * feed again.
 */
@Component
@RequiredArgsConstructor
public class StoreInventoryRefreshPublisher {

    static final String QUEUE_NAME = "store-inventory-refresh-queue";

    // the feeds of one store are imported close to each other; within the delay their requests end in one rebuild
    static final int DELAY_SECONDS = 60;

    private final SqsTemplate sqsTemplate;
    private final StoreInventoryCache cache;

    public void request(String storeId) {
        StoreInventoryRefreshRequest request = new StoreInventoryRefreshRequest(storeId, cache.clockMillis());
        sqsTemplate.send(to -> to.queue(QUEUE_NAME).payload(request).delaySeconds(DELAY_SECONDS));
    }
}
