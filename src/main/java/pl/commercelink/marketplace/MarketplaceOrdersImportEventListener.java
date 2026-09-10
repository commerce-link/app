package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
        if (isNotBlank(payload.getStoreId())) {
            importForStore(payload.getStoreId(), payload.getMarketplace());
            return;
        }
        storesRepository.findAll()
                .stream()
                .filter(s -> s.hasActiveMarketplaceIntegration(payload.getMarketplace()))
                .forEach(s -> handleMarketplaceImport(s, payload.getMarketplace()));
    }

    private void importForStore(String storeId, String marketplace) {
        Store store = storesRepository.findById(storeId);
        if (store == null || !store.hasActiveMarketplaceIntegration(marketplace)) {
            return;
        }
        handleMarketplaceImport(store, marketplace);
    }

    private void handleMarketplaceImport(Store store, String marketplace) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }

        List<MarketplaceOrder> orders = provider.fetchOrders();

        for (MarketplaceOrder order : orders) {
            marketplaceOrderImporter.importOrder(store, marketplace, order);
        }

        store.updateLastFetchedAt(marketplace);
        storesRepository.save(store);
    }

    /** Scheduler payload: {"marketplace":"Allegro"}. */
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
