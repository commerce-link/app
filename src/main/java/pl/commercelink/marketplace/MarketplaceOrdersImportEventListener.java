package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.scheduling.ScheduledExecutionCounter;
import pl.commercelink.scheduling.ScheduledExecution;
import pl.commercelink.starter.util.ElapsedTime;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
@ConditionalOnProperty(name = "marketplace.listeners.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class MarketplaceOrdersImportEventListener {

    private final StoresRepository storesRepository;
    private final MarketplaceOrderImporter marketplaceOrderImporter;
    private final MarketplaceProviderFactory providerFactory;
    private final ScheduledExecutionCounter scheduledExecutionCounter;

    @SqsListener(
            value = "marketplace-orders-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceOrderPayload payload) {
        String marketplace = payload.getMarketplace();
        if (isBlank(payload.getStoreId())) {
            log.error("Marketplace {} orders import rejected: the message names no store", marketplace);
            return;
        }
        List<Store> stores = addressedStore(payload.getStoreId(), marketplace);

        log.info("Marketplace {} orders import started: stores={}", marketplace, stores.size());
        ElapsedTime elapsed = ElapsedTime.started();
        stores.forEach(s -> importOrders(s, marketplace));
        log.info("Marketplace {} orders import finished: stores={} importDurationInMs={}",
                marketplace, stores.size(), elapsed.inMillis());
    }

    private List<Store> addressedStore(String storeId, String marketplace) {
        Store store = storesRepository.findById(storeId);
        if (store == null || !store.hasActiveMarketplaceIntegration(marketplace)) {
            log.warn("Marketplace {} orders import skipped store {}: no active integration", marketplace, storeId);
            return List.of();
        }
        return List.of(store);
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
        scheduledExecutionCounter.countCompleted(store.getStoreId(), ScheduledExecution.ORDERS_IMPORT, marketplace);
    }

    public static class MarketplaceOrderPayload {

        private String marketplace;
        private String storeId;

        public MarketplaceOrderPayload() {
        }

        public MarketplaceOrderPayload(String marketplace, String storeId) {
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
