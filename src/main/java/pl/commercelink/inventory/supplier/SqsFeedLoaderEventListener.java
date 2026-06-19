package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryRepository;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class SqsFeedLoaderEventListener {

    private final SupplierRegistry supplierRegistry;
    private final InventoryRepository inventoryRepository;
    private final StoreSupplierFeedService storeSupplierFeedService;

    @SqsListener(
            value = "supplier-feed-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(FeedLoaderEventPayload payload) {
        try {
            if (isNotBlank(payload.getStoreId())) {
                storeSupplierFeedService.loadStoreFeed(payload.getStoreId(), payload.getSupplierName());
                return;
            }
            supplierRegistry.downloadFeed(payload.getSupplierName()).ifPresent(feedData ->
                    inventoryRepository.store(payload.getSupplierName(), feedData.data(), feedData.extension())
            );
        } catch (Exception e) {
            System.err.println("Failed to download feed: " + e.getMessage());
        }
    }

    public static class FeedLoaderEventPayload {

        private String supplierName;
        private String storeId;

        public FeedLoaderEventPayload() {
        }

        public String getSupplierName() {
            return supplierName;
        }

        public String getStoreId() {
            return storeId;
        }

    }

}
