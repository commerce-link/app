package pl.commercelink.inventory.supplier;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isNotBlank;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class SqsFeedLoaderEventListener {

    static final int MAX_CONFIGURATION_RETRIES = 5;

    private final StoreSupplierFeedService storeSupplierFeedService;
    private final GlobalSupplierFeedService globalSupplierFeedService;
    private final StoreSupplierFeedScheduler feedScheduler;

    @SqsListener(
            value = "supplier-feed-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(FeedLoaderEventPayload payload) throws Exception {
        if (isNotBlank(payload.getStoreId())) {
            loadStoreFeed(payload);
            return;
        }
        globalSupplierFeedService.loadFeed(payload.getSupplierName());
    }

    private void loadStoreFeed(FeedLoaderEventPayload payload) throws Exception {
        try {
            storeSupplierFeedService.loadStoreFeed(payload.getStoreId(), payload.getSupplierName());
        } catch (SupplierConfigurationNotReadyException e) {
            if (payload.getAttempt() >= MAX_CONFIGURATION_RETRIES) {
                throw e;
            }
            feedScheduler.scheduleConfigurationRetry(
                    payload.getStoreId(), payload.getSupplierName(), payload.getAttempt() + 1);
        }
    }

    public static class FeedLoaderEventPayload {

        private String supplierName;
        private String storeId;
        private int attempt;

        public FeedLoaderEventPayload() {
        }

        public String getSupplierName() {
            return supplierName;
        }

        public String getStoreId() {
            return storeId;
        }

        public int getAttempt() {
            return attempt;
        }

    }

}
