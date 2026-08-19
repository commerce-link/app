package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.pricelist.AvailabilityAndPrice;
import pl.commercelink.pricelist.Pricelist;
import pl.commercelink.pricelist.PricelistRepository;
import pl.commercelink.products.*;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                catalog, payload.getMarketplace(), enrichedInventory, pricelist);

        handleMarketplaceExport(store, catalog, payload.getMarketplace(), offers, run);
    }

    private List<MarketplaceOffer> collectOffersFromCatalog(ProductCatalog catalog,
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

            offers.addAll(createMarketplaceOffers(category, marketplaceDefinition, inventory, pricelist));
        }
        return offers;
    }

    private List<MarketplaceOffer> createMarketplaceOffers(CategoryDefinition category,
                                                           MarketplaceDefinition marketplaceDefinition,
                                                           InventoryView inventory,
                                                           Pricelist pricelist) {
        List<MarketplaceOffer> result = new LinkedList<>();

        String categoryName = category.getName();

        for (Product product : productRepository.findAllProductsWithPimId(category.getCategoryId(), true)) {

            Optional<AvailabilityAndPrice> priceInPricelist = pricelist.findByPimId(product.getPimId());

            if (priceInPricelist.isEmpty()) {
                continue;
            }

            if (marketplaceDefinition.isExportSelectedProducts()
                    && !product.isApprovedForMarketplace(marketplaceDefinition.getName())) {
                continue;
            }

            AvailabilityAndPrice availabilityAndPrice = priceInPricelist.get();
            MatchedInventory matchedInventory = inventory.findByProduct(product);

            long qtyToPublish;
            if (marketplaceDefinition.getMinWarehouseQty() > 0) {
                int totalQty = matchedInventory
                        .getInventoryItemsFromSupplier(SupplierRegistry.WAREHOUSE)
                        .stream()
                        .map(InventoryItem::qty)
                        .mapToInt(Integer::intValue)
                        .sum();
                qtyToPublish = totalQty >= marketplaceDefinition.getMinWarehouseQty() ? totalQty : 0L;
            } else {
                boolean hasRequiredTotalQty = matchedInventory.hasTotalMinQty(marketplaceDefinition.getMinTotalQty());
                boolean hasRequiredNumOfDistributorsWithMinQty = matchedInventory.hasOffersFromMultipleSuppliers(
                        marketplaceDefinition.getMinNumOfDistributors(),
                        marketplaceDefinition.getMinQtyPerDistributor()
                );
                qtyToPublish = (hasRequiredTotalQty && hasRequiredNumOfDistributorsWithMinQty)
                        ? matchedInventory.getTotalAvailableQty()
                        : 0L;
            }

            result.add(toMarketplaceOffer(availabilityAndPrice, marketplaceDefinition.getMarkup(), qtyToPublish, categoryName));
        }
        return result;
    }

    private MarketplaceOffer toMarketplaceOffer(AvailabilityAndPrice availabilityAndPrice, double marketplaceMarkup, long totalQuantity, String categoryName) {
        long marketplacePrice = Math.round(availabilityAndPrice.getPrice() * marketplaceMarkup);
        long marketplaceQuantity = Math.min(30, totalQuantity);

        return new MarketplaceOffer(
                availabilityAndPrice.getPimId(),
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
        List<MarketplaceOfferSnapshot> retryableOrphans = retryableOrphans(previousSnapshots, pimIdsOf(currentOffers));
        List<MarketplaceOffer> pendingRemovals = toUnpublishOffers(retryableOrphans);

        boolean providerCalled = !currentOffers.isEmpty() || !pendingRemovals.isEmpty();
        run.offers(buildNextSnapshot(retryableOrphans, currentOffers));

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
                .filter(snapshot -> !currentPimIds.contains(snapshot.pimId()))
                .filter(snapshot -> snapshot.removalAttempts() < removalRetryCount)
                .toList();
    }

    private List<MarketplaceOffer> toUnpublishOffers(List<MarketplaceOfferSnapshot> orphans) {
        return orphans.stream()
                .map(snapshot -> new MarketplaceOffer(
                        snapshot.pimId(), null, null, null, null, null,
                        snapshot.price(), 0L, 0))
                .toList();
    }

    private List<MarketplaceOfferSnapshot> buildNextSnapshot(List<MarketplaceOfferSnapshot> retryableOrphans,
                                                             List<MarketplaceOffer> currentOffers) {
        Stream<MarketplaceOfferSnapshot> incrementedOrphans = retryableOrphans.stream()
                .map(snapshot -> MarketplaceOfferSnapshot.removalPending(
                        snapshot.pimId(), snapshot.price(), snapshot.removalAttempts() + 1));
        Stream<MarketplaceOfferSnapshot> currentAsActive = currentOffers.stream()
                .map(offer -> MarketplaceOfferSnapshot.published(
                        offer.productId(), offer.price(), offer.quantity()));
        return Stream.concat(incrementedOrphans, currentAsActive).toList();
    }
}
