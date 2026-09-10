package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.stores.Store;
import pl.commercelink.starter.util.ElapsedTime;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class MarketplaceReturnsImportEventListener {

    private final StoresRepository storesRepository;
    private final MarketplaceReturnImporter marketplaceReturnImporter;
    private final MarketplaceProviderFactory providerFactory;

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
        List<Store> stores = storesRepository.findAll()
                .stream()
                .filter(s -> s.hasActiveMarketplaceIntegration(marketplace))
                .toList();
        log.info("Marketplace {} returns import started: stores={}", marketplace, stores.size());
        ElapsedTime elapsed = ElapsedTime.started();
        stores.forEach(s -> importReturns(s, marketplace));
        log.info("Marketplace {} returns import finished: stores={} importDurationInMs={}",
                marketplace, stores.size(), elapsed.inMillis());
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
        });
    }

    /** Scheduler payload: {"marketplace":"Allegro"}. */
    public static class MarketplaceReturnsImportPayload {

        private String marketplace;

        public MarketplaceReturnsImportPayload() {
        }

        public String getMarketplace() {
            return marketplace;
        }
    }
}
