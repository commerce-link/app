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
import pl.commercelink.stores.StoresRepository;

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
        storesRepository.findAll()
                .stream()
                .filter(s -> s.hasActiveMarketplaceIntegration(payload.getMarketplace()))
                .forEach(s -> importReturns(s, payload.getMarketplace()));
    }

    // marketplaces without a returns API are skipped silently: MarketplaceProvider.returns() is empty for them
    private void importReturns(Store store, String marketplace) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }
        provider.returns().ifPresent(returns -> {
            for (MarketplaceReturn ret : returns.fetchReturns()) {
                marketplaceReturnImporter.importReturn(store, marketplace, ret);
            }
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
