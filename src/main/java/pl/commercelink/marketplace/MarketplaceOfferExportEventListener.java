package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static pl.commercelink.marketplace.MarketplaceExportSkipReason.CATEGORY_MARKETPLACE_DEFINITION_DISABLED;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.CATEGORY_NOT_CONFIGURED_FOR_MARKETPLACE;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.PRODUCT_DISABLED;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.PRODUCT_NOT_APPROVED_FOR_MARKETPLACE;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.PRODUCT_NOT_IN_PRICELIST;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.PRODUCT_WITHOUT_PIM_ID;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_DISTRIBUTOR_THRESHOLDS;
import static pl.commercelink.marketplace.MarketplaceExportSkipReason.QUANTITY_ZEROED_BELOW_WAREHOUSE_THRESHOLD;

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
                payload.getStoreId(), payload.getMarketplace(), payload.getCatalogId(), payload.getPricelistId());

        InventoryView enrichedInventory = inventory.withEnabledSuppliersAndWarehouseData(store.getStoreId());

        List<MarketplaceOffer> offers = collectOffersFromCatalog(
                catalog, payload.getMarketplace(), enrichedInventory, pricelist, run);

        handleMarketplaceExport(store, catalog, payload.getMarketplace(), offers, run);
    }

    private List<MarketplaceOffer> collectOffersFromCatalog(ProductCatalog catalog,
                                                            String marketplace,
                                                            InventoryView inventory,
                                                            Pricelist pricelist,
                                                            MarketplaceExportRun run) {
        List<MarketplaceOffer> offers = new ArrayList<>();

        for (CategoryDefinition category : catalog.getCategories()) {
            Optional<MarketplaceDefinition> definitionForMarketplace = category.getCategoryDefinition(marketplace);

            if (definitionForMarketplace.isEmpty()) {
                run.excludeCategory(category, CATEGORY_NOT_CONFIGURED_FOR_MARKETPLACE);
                continue;
            }

            MarketplaceDefinition marketplaceDefinition = definitionForMarketplace.get();
            if (!marketplaceDefinition.isEnabled()) {
                run.excludeCategory(category, CATEGORY_MARKETPLACE_DEFINITION_DISABLED);
                continue;
            }

            offers.addAll(createMarketplaceOffers(category, marketplaceDefinition, inventory, pricelist, run));
        }
        return offers;
    }

    private List<MarketplaceOffer> createMarketplaceOffers(CategoryDefinition category,
                                                           MarketplaceDefinition marketplaceDefinition,
                                                           InventoryView inventory,
                                                           Pricelist pricelist,
                                                           MarketplaceExportRun run) {
        List<MarketplaceOffer> categoryOffers = new ArrayList<>();

        for (Product product : productRepository.findAll(category.getCategoryId())) {
            MarketplaceOfferClassification classification =
                    classify(product, marketplaceDefinition, inventory, pricelist);

            if (classification.isExcluded()) {
                run.excludeProduct(category, product, classification.exclusion(), classification.thresholds());
                continue;
            }

            if (classification.quantityZeroedReason() != null) {
                run.excludeProduct(category, product, classification.quantityZeroedReason(), classification.thresholds());
            }

            categoryOffers.add(toMarketplaceOffer(
                    classification.availabilityAndPrice(),
                    marketplaceDefinition.getMarkup(),
                    classification.quantityToPublish(),
                    category.getName()));
        }
        return categoryOffers;
    }

    private MarketplaceOfferClassification classify(Product product,
                                                    MarketplaceDefinition marketplaceDefinition,
                                                    InventoryView inventory,
                                                    Pricelist pricelist) {
        if (!product.isEnabled()) {
            return MarketplaceOfferClassification.excluded(PRODUCT_DISABLED);
        }

        if (StringUtils.isBlank(product.getPimId())) {
            return MarketplaceOfferClassification.excluded(PRODUCT_WITHOUT_PIM_ID);
        }

        Optional<AvailabilityAndPrice> priceInPricelist = pricelist.findByPimId(product.getPimId());
        if (priceInPricelist.isEmpty()) {
            return MarketplaceOfferClassification.excluded(PRODUCT_NOT_IN_PRICELIST);
        }

        if (marketplaceDefinition.isExportSelectedProducts()
                && !product.isApprovedForMarketplace(marketplaceDefinition.getName())) {
            return MarketplaceOfferClassification.excluded(PRODUCT_NOT_APPROVED_FOR_MARKETPLACE);
        }

        AvailabilityAndPrice availabilityAndPrice = priceInPricelist.get();
        MatchedInventory matchedInventory = inventory.findByProduct(product);

        if (marketplaceDefinition.getMinWarehouseQty() > 0) {
            return classifyAgainstWarehouse(availabilityAndPrice, matchedInventory, marketplaceDefinition);
        }
        return classifyAgainstDistributors(availabilityAndPrice, matchedInventory, marketplaceDefinition);
    }

    private MarketplaceOfferClassification classifyAgainstWarehouse(AvailabilityAndPrice availabilityAndPrice,
                                                                    MatchedInventory matchedInventory,
                                                                    MarketplaceDefinition marketplaceDefinition) {
        long warehouseQuantity = matchedInventory
                .getInventoryItemsFromSupplier(SupplierRegistry.WAREHOUSE)
                .stream()
                .mapToLong(InventoryItem::qty)
                .sum();

        if (warehouseQuantity >= marketplaceDefinition.getMinWarehouseQty()) {
            return MarketplaceOfferClassification.published(availabilityAndPrice, warehouseQuantity);
        }
        return MarketplaceOfferClassification.zeroed(
                availabilityAndPrice,
                QUANTITY_ZEROED_BELOW_WAREHOUSE_THRESHOLD,
                MarketplaceExportThresholds.warehouse(warehouseQuantity, marketplaceDefinition.getMinWarehouseQty()));
    }

    private MarketplaceOfferClassification classifyAgainstDistributors(AvailabilityAndPrice availabilityAndPrice,
                                                                       MatchedInventory matchedInventory,
                                                                       MarketplaceDefinition marketplaceDefinition) {
        boolean hasRequiredTotalQuantity = matchedInventory.hasTotalMinQty(marketplaceDefinition.getMinTotalQty());
        boolean hasRequiredNumOfDistributorsWithMinQuantity = matchedInventory.hasOffersFromMultipleSuppliers(
                marketplaceDefinition.getMinNumOfDistributors(),
                marketplaceDefinition.getMinQtyPerDistributor()
        );

        if (hasRequiredTotalQuantity && hasRequiredNumOfDistributorsWithMinQuantity) {
            return MarketplaceOfferClassification.published(
                    availabilityAndPrice, matchedInventory.getTotalAvailableQty());
        }
        return MarketplaceOfferClassification.zeroed(
                availabilityAndPrice,
                QUANTITY_ZEROED_BELOW_DISTRIBUTOR_THRESHOLDS,
                distributorThresholds(matchedInventory, marketplaceDefinition));
    }

    private MarketplaceExportThresholds distributorThresholds(MatchedInventory matchedInventory,
                                                              MarketplaceDefinition marketplaceDefinition) {
        List<InventoryItem> inventoryItems = matchedInventory.getInventoryItems();
        long totalQuantity = inventoryItems.stream().mapToLong(InventoryItem::qty).sum();
        long distributorsWithMinQuantity = inventoryItems.stream()
                .collect(Collectors.groupingBy(InventoryItem::supplier, Collectors.summingLong(InventoryItem::qty)))
                .values()
                .stream()
                .filter(quantity -> quantity >= marketplaceDefinition.getMinQtyPerDistributor())
                .count();

        return MarketplaceExportThresholds.distributors(
                totalQuantity,
                marketplaceDefinition.getMinTotalQty(),
                distributorsWithMinQuantity,
                marketplaceDefinition.getMinNumOfDistributors(),
                marketplaceDefinition.getMinQtyPerDistributor());
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
        run.providerCalled(providerCalled);
        run.offers(buildNextSnapshot(retryableOrphans, currentOffers, run));

        try {
            if (providerCalled) {
                provider.exportOffers(currentOffers, pendingRemovals);
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
                                                             List<MarketplaceOffer> currentOffers,
                                                             MarketplaceExportRun run) {
        Stream<MarketplaceOfferSnapshot> incrementedOrphans = retryableOrphans.stream()
                .map(snapshot -> MarketplaceOfferSnapshot.pendingRemoval(
                        snapshot.pimId(), snapshot.price(), snapshot.removalAttempts() + 1));
        Stream<MarketplaceOfferSnapshot> currentAsActive = currentOffers.stream()
                .map(offer -> MarketplaceOfferSnapshot.published(
                        offer.productId(), offer.price(), offer.quantity(), run.quantityZeroedReason(offer.productId())));
        return Stream.concat(incrementedOrphans, currentAsActive).toList();
    }
}
