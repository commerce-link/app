package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class MarketplaceOrdersImportEventListener {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private MarketplaceOrderImporter marketplaceOrderImporter;

    @Autowired
    private MarketplaceProviderFactory providerFactory;

    @SqsListener(
            value = "marketplace-orders-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceOrderPayload payload) {
        storesRepository.findAll()
                .stream()
                .filter(s -> s.hasActiveMarketplaceIntegration(payload.getMarketplace()))
                .forEach(s -> handleMarketplaceImport(s, payload.getMarketplace()));
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

    public static class MarketplaceOrderPayload {

        private String marketplace;

        public MarketplaceOrderPayload() {
        }

        public String getMarketplace() {
            return marketplace;
        }

    }

}
