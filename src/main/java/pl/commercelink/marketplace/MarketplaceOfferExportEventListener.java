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
import pl.commercelink.taxonomy.UnifiedProductIdentifiers;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

        ExportCandidates candidates = collectOffersFromCatalog(
                store.getStoreId(), catalog, payload.getMarketplace(), enrichedInventory, pricelist);

        handleMarketplaceExport(store, catalog, payload.getMarketplace(), candidates, run);
    }

    /**
     * Offers of every enabled category, one per offer id. Two products can share an offer id (a product still on a
     * source S in one category and the store's clone of S in another), and the marketplace takes one offer per id, so
     * the first one wins: categories in catalog order, products in repository order within a category.
     */
    private ExportCandidates collectOffersFromCatalog(String storeId,
                                                      ProductCatalog catalog,
                                                      String marketplace,
                                                      InventoryView inventory,
                                                      Pricelist pricelist) {
        List<CategoryDefinition> categories = new LinkedList<>();
        List<MarketplaceDefinition> definitions = new LinkedList<>();
        List<List<Product>> productsPerCategory = new LinkedList<>();
        Set<String> exportedPimIds = new HashSet<>();

        for (CategoryDefinition category : catalog.getCategories()) {
            Optional<MarketplaceDefinition> definitionForMarketplace = category.getCategoryDefinition(marketplace);

            if (definitionForMarketplace.isEmpty() || !definitionForMarketplace.get().isEnabled()) {
                continue;
            }

            List<Product> products = productRepository.findAllProductsWithPimId(category.getCategoryId(), true);
            products.forEach(product -> exportedPimIds.add(product.getPimId()));
            categories.add(category);
            definitions.add(definitionForMarketplace.get());
            productsPerCategory.add(products);
        }

        Map<String, MarketplaceOffer> offersByOfferId = new LinkedHashMap<>();
        Set<String> skippedEans = new HashSet<>();
        for (int i = 0; i < categories.size(); i++) {
            for (MarketplaceOffer offer : createMarketplaceOffers(storeId, categories.get(i), definitions.get(i),
                    productsPerCategory.get(i), exportedPimIds, inventory, pricelist, skippedEans)) {
                if (offersByOfferId.putIfAbsent(offer.productId(), offer) != null) {
                    log.warn("Marketplace {} export of store {}: more than one product maps to offer id {}; "
                            + "keeping the first and skipping the product of category {}",
                            marketplace, storeId, offer.productId(), categories.get(i).getName());
                }
            }
        }
        return new ExportCandidates(new LinkedList<>(offersByOfferId.values()), skippedEans);
    }

    /**
     * The offers of this run, and the unified EANs of the products skipped because this instance's index copy does not
     * know their entry yet (see {@link #createMarketplaceOffers}).
     */
    private record ExportCandidates(List<MarketplaceOffer> offers, Set<String> skippedEans) {
    }

    private List<MarketplaceOffer> createMarketplaceOffers(String storeId,
                                                           CategoryDefinition category,
                                                           MarketplaceDefinition marketplaceDefinition,
                                                           List<Product> products,
                                                           Set<String> exportedPimIds,
                                                           InventoryView inventory,
                                                           Pricelist pricelist,
                                                           Set<String> skippedEans) {
        List<MarketplaceOffer> result = new LinkedList<>();

        String categoryName = category.getName();

        for (Product product : products) {

            // The pricelist is keyed by the id of its build time: the source, the clone, or (when the clone was
            // deleted after the build) a clone the product no longer points at, which only the codes still find.
            // The code fallback never takes the row of another product of this export.
            Optional<AvailabilityAndPrice> priceInPricelist = aliases.find(storeId, pricelist, product.getPimId())
                    .or(() -> pricelist.findByCodes(product.getEan(), product.getManufacturerCode(), exportedPimIds));

            if (priceInPricelist.isEmpty()) {
                continue;
            }

            if (marketplaceDefinition.isExportSelectedProducts()
                    && !product.isApprovedForMarketplace(marketplaceDefinition.getName())) {
                continue;
            }

            // An index copy older than the product's entry (a clone made moments ago) cannot tell the offer id, which
            // would come out as the clone's own id: the product waits for a run on a fresher copy, and the offer it was
            // published under is kept until then (see handleMarketplaceExport).
            if (!aliases.knows(storeId, product.getPimId())) {
                log.warn("Marketplace export of store {}: product {} skipped for this run, the index copy does not "
                        + "know its PIM entry {} yet", storeId, product.getProductId(), product.getPimId());
                unifiedEan(product.getEan()).ifPresent(skippedEans::add);
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
                                         ExportCandidates candidates,
                                         MarketplaceExportRun run) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }

        List<MarketplaceOffer> currentOffers = candidates.offers();
        Set<String> currentPimIds = pimIdsOf(currentOffers);

        // A previous offer with the EAN of a skipped product is most likely that product's offer under the id the
        // stale copy cannot produce: it is carried over as it was, neither taken off nor counted as an attempt.
        Map<Boolean, List<MarketplaceOfferSnapshot>> byWaiting = loadPreviousSnapshot(store, catalog, marketplace).stream()
                .collect(Collectors.partitioningBy(snapshot -> !currentPimIds.contains(snapshot.pimId())
                        && unifiedEan(snapshot.ean()).filter(candidates.skippedEans()::contains).isPresent()));
        List<MarketplaceOfferSnapshot> waiting = byWaiting.get(true);
        List<MarketplaceOfferSnapshot> previousSnapshots = byWaiting.get(false);

        List<MarketplaceOfferSnapshot> retryableOrphans = retryableOrphans(previousSnapshots, currentPimIds);
        List<MarketplaceOfferSnapshot> abandonedRemovals = abandonedRemovals(previousSnapshots, currentPimIds);
        List<MarketplaceOffer> pendingRemovals = toUnpublishOffers(retryableOrphans);

        boolean providerCalled = !currentOffers.isEmpty() || !pendingRemovals.isEmpty();
        run.offers(buildNextSnapshot(waiting, retryableOrphans, abandonedRemovals, currentOffers));

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

    /**
     * The offer ids of this run. A previous offer is an orphan, to be taken off, when no current offer has its id: every
     * marketplace keys an offer by our id (Allegro external id, CS-Cart product code, Empik shop SKU, Morele product id),
     * so another product with the same EAN does not keep it alive.
     */
    private Set<String> pimIdsOf(List<MarketplaceOffer> offers) {
        return offers.stream()
                .map(MarketplaceOffer::productId)
                .collect(Collectors.toSet());
    }

    private static Optional<String> unifiedEan(String ean) {
        return Optional.ofNullable(UnifiedProductIdentifiers.unifyEan(ean)).filter(code -> !code.isBlank());
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

    private List<MarketplaceOfferSnapshot> buildNextSnapshot(List<MarketplaceOfferSnapshot> waiting,
                                                             List<MarketplaceOfferSnapshot> retryableOrphans,
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
        return Stream.of(waiting.stream(), incrementedOrphans, failedRemovals, currentAsActive).flatMap(stream -> stream).toList();
    }
}
