package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.*;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
@RequiredArgsConstructor
public class MarketplaceOfferExportEventListener {

    private final StoresRepository storesRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final PricelistRepository pricelistRepository;
    private final Inventory inventory;
    private final MarketplaceProviderFactory providerFactory;
    private final MarketplaceExportRunService marketplaceExportRunService;
    private final StoreActivity storeActivity;
    private final StorePimAliases aliases;

    @Value("${marketplace.export.removalAttempts:3}")
    private int removalRetryCount;

    @SqsListener(
            value = "marketplace-offer-export-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(MarketplaceOfferExportRequest payload) {
        Store store = storesRepository.findById(payload.getStoreId());
        // an export reaching an inactive store would put back the offers its deactivation withdrew
        if (!storeActivity.isActive(store)) {
            log.warn("Marketplace {} offer export skipped for store {}: the store is inactive",
                    payload.getMarketplace(), payload.getStoreId());
            return;
        }
        if (!store.hasActiveMarketplaceIntegration(payload.getMarketplace())) {
            return;
        }

        ProductCatalog catalog = productCatalogRepository.findById(payload.getStoreId(), payload.getCatalogId());
        if (!catalog.isMarketplaceExportEnabled(payload.getMarketplace())) {
            return;
        }

        Pricelist pricelist = pricelistRepository.find(payload.getStoreId(), payload.getCatalogId(), payload.getPricelistId());
        if (pricelist == null) {
            return;
        }

        MarketplaceExportRun run = new MarketplaceExportRun(
                payload.getStoreId(), payload.getMarketplace(), payload.getCatalogId());

        InventoryView enrichedInventory = inventory.withEnabledSuppliersAndWarehouseData(store.getStoreId());

        List<MarketplaceOffer> offers = collectOffersFromCatalog(
                store.getStoreId(), catalog, payload.getMarketplace(), enrichedInventory, pricelist);

        handleMarketplaceExport(store, catalog, payload.getMarketplace(), offers, run);
    }

    private List<MarketplaceOffer> collectOffersFromCatalog(String storeId,
                                                            ProductCatalog catalog,
                                                            String marketplace,
                                                            InventoryView inventory,
                                                            Pricelist pricelist) {
        List<MarketplaceOffer> offers = new LinkedList<>();

        for (CategoryDefinition category : catalog.getCategories()) {
            Optional<MarketplaceDefinition> definitionForMarketplace = category.getCategoryDefinition(marketplace);

            if (definitionForMarketplace.isEmpty()) {
                continue;
            }

            MarketplaceDefinition marketplaceDefinition = definitionForMarketplace.get();
            if (!marketplaceDefinition.isEnabled()) {
                continue;
            }

            offers.addAll(createMarketplaceOffers(storeId, category, marketplaceDefinition, inventory, pricelist));
        }
        return offers;
    }

    private List<MarketplaceOffer> createMarketplaceOffers(String storeId,
                                                           CategoryDefinition category,
                                                           MarketplaceDefinition marketplaceDefinition,
                                                           InventoryView inventory,
                                                           Pricelist pricelist) {
        List<MarketplaceOffer> result = new LinkedList<>();

        String categoryName = category.getName();

        for (Product product : productRepository.findAllProductsWithPimId(category.getCategoryId(), true)) {

            // The pricelist is keyed by the id of its build time: the source, the clone, or (when the clone was
            // deleted after the build) a clone the product no longer points at, which only the codes still find.
            Optional<AvailabilityAndPrice> priceInPricelist = aliases.find(storeId, pricelist, product.getPimId())
                    .or(() -> pricelist.findByCodes(product.getEan(), product.getManufacturerCode()));

            if (priceInPricelist.isEmpty()) {
                continue;
            }

            if (marketplaceDefinition.isExportSelectedProducts()
                    && !product.isApprovedForMarketplace(marketplaceDefinition.getName())) {
                continue;
            }

            AvailabilityAndPrice availabilityAndPrice = priceInPricelist.get();
            MatchedInventory matchedInventory =
                    inventory.findByProduct(product).atPricePoint(availabilityAndPrice.getPrice());

            long qtyToPublish = marketplaceDefinition.qtyToPublish(matchedInventory);

            // The offer keeps the id it was published under: a product on a clone is still offered as its source.
            String offerId = aliases.offerIdOf(storeId, product.getPimId());

            result.add(toMarketplaceOffer(offerId, availabilityAndPrice, marketplaceDefinition.getMarkup(), qtyToPublish, categoryName));
        }
        return result;
    }

    private MarketplaceOffer toMarketplaceOffer(String offerId, AvailabilityAndPrice availabilityAndPrice, double marketplaceMarkup, long totalQuantity, String categoryName) {
        long marketplacePrice = Math.round(availabilityAndPrice.getPrice() * marketplaceMarkup);
        long marketplaceQuantity = Math.min(30, totalQuantity);

        return new MarketplaceOffer(
                offerId,
                availabilityAndPrice.getEan(),
                availabilityAndPrice.getManufacturerCode(),
                availabilityAndPrice.getBrand(),
                availabilityAndPrice.getName(),
                categoryName,
                marketplacePrice,
                marketplaceQuantity,
                availabilityAndPrice.getEstimatedDeliveryDays()
        );
    }

    private void handleMarketplaceExport(Store store,
                                         ProductCatalog catalog,
                                         String marketplace,
                                         List<MarketplaceOffer> currentOffers,
                                         MarketplaceExportRun run) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }

        List<MarketplaceOfferSnapshot> previousSnapshots = loadPreviousSnapshot(store, catalog, marketplace);
        Set<String> currentPimIds = pimIdsOf(currentOffers);
        List<MarketplaceOfferSnapshot> retryableOrphans = retryableOrphans(previousSnapshots, currentPimIds);
        List<MarketplaceOfferSnapshot> abandonedRemovals = abandonedRemovals(previousSnapshots, currentPimIds);
        List<MarketplaceOffer> pendingRemovals = toUnpublishOffers(retryableOrphans);

        boolean providerCalled = !currentOffers.isEmpty() || !pendingRemovals.isEmpty();
        run.offers(buildNextSnapshot(retryableOrphans, abandonedRemovals, currentOffers));

        try {
            if (providerCalled) {
                provider.exportOffers(currentOffers, pendingRemovals, run::rejected);
            }
        } catch (RuntimeException exception) {
            run.failed(exception);
            marketplaceExportRunService.saveRun(run);
            throw exception;
        }

        marketplaceExportRunService.saveRun(run);
    }

    private List<MarketplaceOfferSnapshot> loadPreviousSnapshot(Store store, ProductCatalog catalog, String marketplace) {
        return marketplaceExportRunService.loadPreviousExport(
                store.getStoreId(), catalog.getCatalogId(), marketplace
        );
    }

    private Set<String> pimIdsOf(List<MarketplaceOffer> offers) {
        return offers.stream()
                .map(MarketplaceOffer::productId)
                .collect(Collectors.toSet());
    }

    private List<MarketplaceOfferSnapshot> retryableOrphans(List<MarketplaceOfferSnapshot> previousSnapshots, Set<String> currentPimIds) {
        return previousSnapshots.stream()
                .filter(snapshot -> isOrphan(snapshot, currentPimIds))
                .filter(snapshot -> snapshot.removalAttempts() < removalRetryCount)
                .toList();
    }

    // Only a removal the marketplace refused on its last attempt is an alert: a removal it accepted is simply done.
    private List<MarketplaceOfferSnapshot> abandonedRemovals(List<MarketplaceOfferSnapshot> previousSnapshots, Set<String> currentPimIds) {
        List<MarketplaceOfferSnapshot> abandoned = previousSnapshots.stream()
                .filter(snapshot -> isOrphan(snapshot, currentPimIds))
                .filter(snapshot -> snapshot.removalAttempts() > 0 && snapshot.removalAttempts() >= removalRetryCount)
                .filter(snapshot -> MarketplaceOfferSnapshot.OUTCOME_REJECTED.equals(snapshot.outcome()))
                .toList();
        abandoned.forEach(snapshot -> log.error(
                "Marketplace offer {} (EAN {}) could not be taken off after {} attempts and stays on the marketplace: {} {}",
                snapshot.pimId(), snapshot.ean(), snapshot.removalAttempts(), snapshot.reasonCode(), snapshot.message()));
        return abandoned;
    }

    private boolean isOrphan(MarketplaceOfferSnapshot snapshot, Set<String> currentPimIds) {
        return !currentPimIds.contains(snapshot.pimId())
                && !MarketplaceOfferSnapshot.OUTCOME_REMOVAL_FAILED.equals(snapshot.outcome());
    }

    // The marketplace matches an offer by its EAN, so the one it was published with has to be sent again to take it off.
    private List<MarketplaceOffer> toUnpublishOffers(List<MarketplaceOfferSnapshot> orphans) {
        return orphans.stream()
                .map(snapshot -> new MarketplaceOffer(
                        snapshot.pimId(), snapshot.ean(), snapshot.manufacturerCode(), null, null, null,
                        snapshot.price(), 0L, 0))
                .toList();
    }

    private List<MarketplaceOfferSnapshot> buildNextSnapshot(List<MarketplaceOfferSnapshot> retryableOrphans,
                                                             List<MarketplaceOfferSnapshot> abandonedRemovals,
                                                             List<MarketplaceOffer> currentOffers) {
        Stream<MarketplaceOfferSnapshot> incrementedOrphans = retryableOrphans.stream()
                .map(snapshot -> MarketplaceOfferSnapshot.removalPending(
                        snapshot.pimId(), snapshot.price(), snapshot.removalAttempts() + 1,
                        snapshot.ean(), snapshot.manufacturerCode()));
        Stream<MarketplaceOfferSnapshot> failedRemovals = abandonedRemovals.stream()
                .map(snapshot -> MarketplaceOfferSnapshot.removalFailed(
                        snapshot.pimId(), snapshot.price(), snapshot.removalAttempts(),
                        snapshot.reasonCode(), snapshot.message(), snapshot.ean(), snapshot.manufacturerCode()));
        Stream<MarketplaceOfferSnapshot> currentAsActive = currentOffers.stream()
                .map(offer -> MarketplaceOfferSnapshot.published(
                        offer.productId(), offer.price(), offer.quantity(), offer.ean(), offer.manufacturerCode()));
        return Stream.of(incrementedOrphans, failedRemovals, currentAsActive).flatMap(stream -> stream).toList();
    }
}
