package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryRepository;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class SqsFeedLoaderEventListener {

    private final SupplierRegistry supplierRegistry;
    private final InventoryRepository inventoryRepository;

    public SqsFeedLoaderEventListener(SupplierRegistry supplierRegistry, InventoryRepository inventoryRepository) {
        this.supplierRegistry = supplierRegistry;
        this.inventoryRepository = inventoryRepository;
    }

    @SqsListener(
            value = "supplier-feed-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(FeedLoaderEventPayload payload) {
        try {
            supplierRegistry.downloadFeed(payload.getSupplierName()).ifPresent(feedData ->
                    inventoryRepository.store(payload.getSupplierName(), feedData.data(), feedData.extension())
            );
        } catch (Exception e) {
            System.err.println("Failed to download feed: " + e.getMessage());
        }
    }

    public static class FeedLoaderEventPayload {

        private String supplierName;

        public FeedLoaderEventPayload() {
        }

        public String getSupplierName() {
            return supplierName;
        }

    }

}
