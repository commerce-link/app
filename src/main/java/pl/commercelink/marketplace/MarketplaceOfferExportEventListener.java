package pl.commercelink.marketplace;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class MarketplaceOfferExportEventListener {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PricelistRepository pricelistRepository;

    @Autowired
    private Inventory inventory;

    @Autowired
    private MarketplaceProviderFactory providerFactory;

    @Autowired
    private MarketplaceOfferExportRepository marketplaceOfferExportRepository;

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

        Pricelist pricelist = pricelistRepository.find(payload.getCatalogId(), payload.getPricelistId());
        if (pricelist == null) {
            return;
        }

        InventoryView enrichedInventory = inventory.withEnabledSuppliersAndWarehouseData(store.getStoreId());

        List<MarketplaceOffer> offers = new LinkedList<>();
        for (CategoryDefinition category : catalog.getCategories()) {
            Optional<MarketplaceDefinition> op = category.getCategoryDefinition(payload.getMarketplace());

            if (op.isPresent()) {
                MarketplaceDefinition marketplaceDefinition = op.get();

                if (!marketplaceDefinition.isEnabled()) {
                    continue;
                }

                offers.addAll(createMarketplaceOffers(category, marketplaceDefinition, enrichedInventory, pricelist));
            }
        }

        handleMarketplaceExport(store, catalog, payload.getMarketplace(), offers);
    }

    private List<MarketplaceOffer> createMarketplaceOffers(CategoryDefinition category, MarketplaceDefinition marketplaceDefinition, InventoryView inventory, Pricelist pricelist) {
        List<MarketplaceOffer> result = new LinkedList<>();

        String categoryName = ProductGroupLocalization.INSTANCE.name(category.getCategory().getProductGroup())
                + " / " + ProductCategoryLocalization.INSTANCE.name(category.getCategory());

        for (Product product : productRepository.findAllProductsWithPimId(category.getCategoryId(), true)) {

            Optional<AvailabilityAndPrice> op = pricelist.findByPimId(product.getPimId());

            if (!op.isPresent()) {
                continue;
            }

            if (marketplaceDefinition.isExportSelectedProducts() && !product.isApprovedForMarketplace(marketplaceDefinition.getName())) {
                continue;
            }

            AvailabilityAndPrice availabilityAndPrice = op.get();
            MatchedInventory matchedInventory = inventory.findByProduct(product);

            if (marketplaceDefinition.getMinWarehouseQty() > 0) {

                int totalQty = matchedInventory
                        .getInventoryItemsFromSupplier(SupplierRegistry.WAREHOUSE)
                        .stream()
                        .map(InventoryItem::qty)
                        .mapToInt(Integer::intValue)
                        .sum();

                if (totalQty >= marketplaceDefinition.getMinWarehouseQty()) {
                    result.add(toMarketplaceOffer(availabilityAndPrice, marketplaceDefinition.getMarkup(), totalQty, categoryName));
                }

            } else {

                boolean hasRequiredTotalQty = matchedInventory.hasTotalMinQty(marketplaceDefinition.getMinTotalQty());
                boolean hasRequiredNumOfDistributorsWithMinQty = matchedInventory.hasOffersFromMultipleSuppliers(
                        marketplaceDefinition.getMinNumOfDistributors(),
                        marketplaceDefinition.getMinQtyPerDistributor()
                );

                if (hasRequiredTotalQty & hasRequiredNumOfDistributorsWithMinQty) {
                    result.add(toMarketplaceOffer(availabilityAndPrice, marketplaceDefinition.getMarkup(), matchedInventory.getTotalAvailableQty(), categoryName));
                }
            }
        }
        return result;
    }

    private MarketplaceOffer toMarketplaceOffer(AvailabilityAndPrice availabilityAndPrice, double marketplaceMarkup, long totalQty, String categoryName) {
        long marketplacePrice = Math.round(availabilityAndPrice.getPrice() * marketplaceMarkup);
        long marketplaceQty = Math.min(30, totalQty);

        return new MarketplaceOffer(
                availabilityAndPrice.getPimId(),
                availabilityAndPrice.getEan(),
                availabilityAndPrice.getManufacturerCode(),
                availabilityAndPrice.getBrand(),
                availabilityAndPrice.getName(),
                categoryName,
                marketplacePrice,
                marketplaceQty,
                availabilityAndPrice.getEstimatedDeliveryDays()
        );
    }

    private void handleMarketplaceExport(Store store, ProductCatalog catalog, String marketplace, List<MarketplaceOffer> offers) {
        MarketplaceProvider provider = providerFactory.get(store, marketplace);
        if (provider == null) {
            return;
        }

        List<MarketplaceOfferSnapshot> previousSnapshots = marketplaceOfferExportRepository.loadPreviousExport(
                store.getStoreId(),
                catalog.getCatalogId(),
                marketplace
        );

        List<MarketplaceOffer> offersToRemove = previousSnapshots.stream()
                .filter(ps -> offers.stream().noneMatch(o -> o.productId().equals(ps.getPimId())))
                .map(ps -> new MarketplaceOffer(ps.getPimId(), null, null, null, null, null, ps.getPrice(), ps.getQty(), 0))
                .collect(Collectors.toList());

        List<MarketplaceOffer> offersToPublish = offers.stream()
                .filter(offer -> {
                    Optional<MarketplaceOfferSnapshot> previousSnapshot = previousSnapshots.stream()
                            .filter(ps -> ps.hasPimId(offer.productId()))
                            .findFirst();
                    return !previousSnapshot.isPresent() || previousSnapshot.get().getPrice() != offer.price() || previousSnapshot.get().getQty() != offer.quantity();
                })
                .collect(Collectors.toList());

        if (!offersToPublish.isEmpty() || !offersToRemove.isEmpty()) {
            provider.exportOffers(offersToPublish, offersToRemove);
        }

        List<MarketplaceOfferSnapshot> currentSnapshots = offers.stream()
                .map(o -> new MarketplaceOfferSnapshot(o.productId(), o.price(), o.quantity()))
                .collect(Collectors.toList());

        marketplaceOfferExportRepository.saveCurrentExport(
                store.getStoreId(),
                catalog.getCatalogId(),
                marketplace,
                currentSnapshots
        );
    }
}
