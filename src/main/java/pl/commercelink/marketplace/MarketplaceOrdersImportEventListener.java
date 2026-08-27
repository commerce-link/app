package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.List;


@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class MarketplaceOrdersImportEventListener {

    public static final String SCOPE_RETURNS = "returns";

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private MarketplaceOrderImporter marketplaceOrderImporter;

    @Autowired
    private MarketplaceReturnImporter marketplaceReturnImporter;

    @Autowired
    private MarketplaceProviderFactory providerFactory;

    @SqsListener(
            value = "marketplace-orders-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceOrderPayload payload) {
        boolean returnsScope = SCOPE_RETURNS.equals(payload.getScope());
        storesRepository.findAll()
                .stream()
                .filter(s -> s.hasActiveMarketplaceIntegration(payload.getMarketplace()))
                .forEach(s -> {
                    if (returnsScope) {
                        handleReturnsImport(s, payload.getMarketplace());
                    } else {
                        handleMarketplaceImport(s, payload.getMarketplace());
                    }
                });
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

    // marketplaces without a returns API are skipped silently: MarketplaceProvider.returns() is empty for them
    private void handleReturnsImport(Store store, String marketplace) {
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

    /** Scheduler payload: {"marketplace":"Allegro"} imports orders; {"marketplace":"Allegro","scope":"returns"} imports returns. */
    public static class MarketplaceOrderPayload {

        private String marketplace;
        private String scope;

        public MarketplaceOrderPayload() {
        }

        public String getMarketplace() {
            return marketplace;
        }

        public String getScope() {
            return scope;
        }

    }

}
