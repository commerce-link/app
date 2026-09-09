package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.starter.util.ElapsedTime;
import pl.commercelink.stores.StoresRepository;

import java.util.List;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class MarketplaceOrdersImportEventListener {

    private final StoresRepository storesRepository;
    private final MarketplaceOrderImporter marketplaceOrderImporter;
    private final MarketplaceProviderFactory providerFactory;

    @SqsListener(
            value = "marketplace-orders-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceOrderPayload payload) {
        String marketplace = payload.getMarketplace();
        List<Store> stores = storesRepository.findAll()
                .stream()
                .filter(s -> s.hasActiveMarketplaceIntegration(marketplace))
                .toList();

        log.info("Marketplace {} orders import started: stores={}", marketplace, stores.size());
        ElapsedTime elapsed = ElapsedTime.started();
        stores.forEach(s -> importOrders(s, marketplace));
        log.info("Marketplace {} orders import finished: stores={} importDurationInMs={}",
                marketplace, stores.size(), elapsed.inMillis());
    }

    private void importOrders(Store store, String marketplace) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            // an active integration without a provider means the adapter jar or its credentials are missing
            log.error("Marketplace {} orders import has no provider for store {}: nothing will be imported",
                    marketplace, store.getStoreId());
            return;
        }

        ElapsedTime elapsed = ElapsedTime.started();
        List<MarketplaceOrder> orders = provider.fetchOrders();
        long fetchDurationInMs = elapsed.inMillis();

        int imported = 0;
        for (MarketplaceOrder order : orders) {
            if (marketplaceOrderImporter.importOrder(store, marketplace, order)) {
                imported++;
            }
        }

        store.updateLastFetchedAt(marketplace);
        storesRepository.save(store);
        log.info("Marketplace {} orders import store={}: fetched={} imported={} duplicates={}"
                        + " fetchDurationInMs={} importDurationInMs={}",
                marketplace, store.getStoreId(), orders.size(), imported, orders.size() - imported,
                fetchDurationInMs, elapsed.inMillis());
    }

    /** Scheduler payload: {"marketplace":"Allegro"}. */
    public static class MarketplaceOrderPayload {

        private String marketplace;

        public MarketplaceOrderPayload() {
        }

        public String getMarketplace() {
            return marketplace;
        }
    }

}
