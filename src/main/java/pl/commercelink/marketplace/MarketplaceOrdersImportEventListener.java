package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
@RequiredArgsConstructor
public class MarketplaceOrdersImportEventListener {

    public static final String SCOPE_ORDERS = "orders";
    public static final String SCOPE_RETURNS = "returns";

    private final StoresRepository storesRepository;
    private final MarketplaceOrderImporter marketplaceOrderImporter;
    private final MarketplaceReturnImporter marketplaceReturnImporter;
    private final MarketplaceProviderFactory providerFactory;

    @Value("${marketplace.returns.enabled:true}")
    private boolean returnsEnabled = true;

    @SqsListener(
            value = "marketplace-orders-import-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceOrderPayload payload) {
        String scope = payload.getScope();
        if (scope != null && !scope.isBlank() && !SCOPE_ORDERS.equals(scope) && !SCOPE_RETURNS.equals(scope)) {
            // Fail closed: an unrecognised scope must not fall back to a full orders import (e.g. during a rollback).
            log.error("Unknown marketplace import scope {}; message ignored", scope);
            return;
        }
        boolean returnsScope = SCOPE_RETURNS.equals(scope);
        if (returnsScope && !returnsEnabled) {
            log.warn("marketplace.returns.enabled=false: skipping returns import");
            return;
        }
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
