package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.scheduling.ScheduledExecutionCounter;
import pl.commercelink.scheduling.ScheduledExecution;
import pl.commercelink.stores.Store;
import pl.commercelink.starter.util.ElapsedTime;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
@ConditionalOnProperty(name = "marketplace.listeners.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class MarketplaceReturnsImportEventListener {

    private final StoresRepository storesRepository;
    private final MarketplaceReturnImporter marketplaceReturnImporter;
    private final MarketplaceProviderFactory providerFactory;
    private final ScheduledExecutionCounter scheduledExecutionCounter;

    @Value("${marketplace.returns.enabled:true}")
    private boolean returnsEnabled = true;

    @SqsListener(
            value = "marketplace-returns-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceReturnsImportPayload payload) {
        if (!returnsEnabled) {
            log.warn("marketplace.returns.enabled=false: skipping returns import");
            return;
        }
        String marketplace = payload.getMarketplace();
        if (isBlank(payload.getStoreId())) {
            log.error("Marketplace {} returns import rejected: the message names no store", marketplace);
            return;
        }
        Store store = storesRepository.findById(payload.getStoreId());
        if (store == null || !store.hasActiveMarketplaceIntegration(marketplace)) {
            log.warn("Marketplace {} returns import skipped store {}: no active integration", marketplace, payload.getStoreId());
            return;
        }
        log.info("Marketplace {} returns import started: store={}", marketplace, store.getStoreId());
        ElapsedTime elapsed = ElapsedTime.started();
        importReturns(store, marketplace);
        log.info("Marketplace {} returns import finished: store={} importDurationInMs={}",
                marketplace, store.getStoreId(), elapsed.inMillis());
    }

    // marketplaces without a returns API are skipped silently: MarketplaceProvider.returns() is empty for them
    private void importReturns(Store store, String marketplace) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            // an active integration without a provider means the adapter jar or its credentials are missing
            log.error("Marketplace {} returns import has no provider for store {}: nothing will be imported",
                    marketplace, store.getStoreId());
            return;
        }
        provider.returns().ifPresent(returns -> {
            ElapsedTime elapsed = ElapsedTime.started();
            List<MarketplaceReturn> fetched = returns.fetchReturns();
            long fetchDurationInMs = elapsed.inMillis();

            for (MarketplaceReturn ret : fetched) {
                marketplaceReturnImporter.importReturn(store, marketplace, ret);
            }

            log.info("Marketplace {} returns import store={}: fetched={} fetchDurationInMs={}"
                            + " importDurationInMs={}",
                    marketplace, store.getStoreId(), fetched.size(), fetchDurationInMs, elapsed.inMillis());
            scheduledExecutionCounter.countCompleted(store.getStoreId(), ScheduledExecution.RETURNS_IMPORT, marketplace);
        });
    }

    public static class MarketplaceReturnsImportPayload {

        private String marketplace;
        private String storeId;

        public MarketplaceReturnsImportPayload() {
        }

        public MarketplaceReturnsImportPayload(String marketplace, String storeId) {
            this.marketplace = marketplace;
            this.storeId = storeId;
        }

        public String getMarketplace() {
            return marketplace;
        }

        public String getStoreId() {
            return storeId;
        }
    }
}
