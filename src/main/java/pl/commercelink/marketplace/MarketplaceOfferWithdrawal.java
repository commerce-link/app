package pl.commercelink.marketplace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.MarketplaceIntegration;
import pl.commercelink.stores.Store;

import java.util.List;

/**
 * Takes a store's offers off sale on every marketplace by sending each offer of its last export again with no stock.
 * The run is saved like an export run, so the first export after the store is active again starts from it and
 * brings the offers back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceOfferWithdrawal {

    private final ProductCatalogRepository productCatalogRepository;
    private final MarketplaceProviderFactory providerFactory;
    private final MarketplaceExportRunService marketplaceExportRunService;

    /** Throws when a marketplace call fails, so the caller can try the whole withdrawal again later. */
    public void withdrawAll(Store store) {
        List<ProductCatalog> catalogs = productCatalogRepository.findAll(store.getStoreId());
        for (MarketplaceIntegration integration : store.getMarketplaces()) {
            withdraw(store, integration, catalogs);
        }
    }

    private void withdraw(Store store, MarketplaceIntegration integration, List<ProductCatalog> catalogs) {
        String marketplace = integration.getName();
        if (!integration.isLoggedIn()) {
            log.warn("Offers of store {} on {} not withdrawn: the marketplace connection is lost",
                    store.getStoreId(), marketplace);
            return;
        }
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            // an active integration without a provider means the adapter jar or its credentials are missing
            log.error("Offers of store {} on {} not withdrawn: no provider, they stay on sale",
                    store.getStoreId(), marketplace);
            return;
        }
        for (ProductCatalog catalog : catalogs) {
            withdraw(store, marketplace, catalog, provider);
        }
    }

    private void withdraw(Store store, String marketplace, ProductCatalog catalog, MarketplaceProvider provider) {
        List<MarketplaceOfferSnapshot> exported = marketplaceExportRunService.loadPreviousExport(
                store.getStoreId(), catalog.getCatalogId(), marketplace);
        if (exported.isEmpty()) {
            return;
        }
        MarketplaceExportRun run = new MarketplaceExportRun(store.getStoreId(), marketplace, catalog.getCatalogId());
        run.offers(exported.stream()
                .map(snapshot -> MarketplaceOfferSnapshot.removalPending(
                        snapshot.pimId(), snapshot.price(), snapshot.removalAttempts() + 1))
                .toList());
        List<MarketplaceOffer> withdrawals = exported.stream()
                .map(snapshot -> new MarketplaceOffer(
                        snapshot.pimId(), null, null, null, null, null, snapshot.price(), 0L, 0))
                .toList();
        try {
            provider.exportOffers(List.of(), withdrawals, run::rejected);
        } catch (RuntimeException exception) {
            run.failed(exception);
            marketplaceExportRunService.saveRun(run);
            throw exception;
        }
        marketplaceExportRunService.saveRun(run);
    }
}
