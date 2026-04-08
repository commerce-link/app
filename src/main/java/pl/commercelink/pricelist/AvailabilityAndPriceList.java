package pl.commercelink.pricelist;

import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.*;

import java.util.*;
import java.util.stream.Collectors;

public class AvailabilityAndPriceList {

    private final InventoryView inventory;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final ProductRecommendationEngine recommendationEngine;
    private final ProductPricingStrategy pricing;
    private final SellingPriceHistoryRepository sellingPriceHistoryRepository;

    public AvailabilityAndPriceList(
            InventoryView inventory,
            ProductCatalogRepository productCatalogRepository,
            ProductRepository productRepository,
            ProductRecommendationEngine recommendationEngine,
            RollingPriceAggregateRepository rollingPriceAggregateRepository,
            SellingPriceHistoryRepository sellingPriceHistoryRepository
    ) {
        this.inventory = inventory;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.recommendationEngine = recommendationEngine;
        this.sellingPriceHistoryRepository = sellingPriceHistoryRepository;

        Map<String, RollingPriceAggregate> priceAggregates = rollingPriceAggregateRepository.loadAll();
        this.pricing = new ProductPricingStrategy(inventory, priceAggregates);
    }

    public List<AvailabilityAndPrice> generate(String storeId, String catalogId) {
        Map<String, SellingPriceHistory> histories = sellingPriceHistoryRepository.load(catalogId);
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        return generate(catalog.getCategories(), histories);
    }

    public List<AvailabilityAndPrice> generate(String storeId, String catalogId, String categoryId) {
        Map<String, SellingPriceHistory> histories = sellingPriceHistoryRepository.load(catalogId);
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        return generate(catalog.getCategories().stream().filter(c -> c.getCategoryId().equals(categoryId)).collect(Collectors.toList()), histories);
    }

    private List<AvailabilityAndPrice> generate(List<CategoryDefinition> categories, Map<String, SellingPriceHistory> histories) {
        return categories.stream()
                .map(c -> {
                    if (c.hasType(CategoryDefinitionType.Dynamic)) {
                        return getAvailabilityAndPricesForDynamicCatalog(c, histories);
                    }
                    return getAvailabilityAndPricesForManagedCatalog(c, histories);
                })
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private List<AvailabilityAndPrice> getAvailabilityAndPricesForManagedCatalog(CategoryDefinition categoryDefinition, Map<String, SellingPriceHistory> histories) {
        List<Product> products = productRepository.findAllProductsWithPimId(categoryDefinition.getCategoryId(), true);
        return getAvailabilityAndPrices(products, categoryDefinition, histories);
    }

    private List<AvailabilityAndPrice> getAvailabilityAndPricesForDynamicCatalog(CategoryDefinition categoryDefinition, Map<String, SellingPriceHistory> histories) {
        List<ProductRecommendation> recommendations = recommendationEngine.getRecommendationsForMappedProducts(categoryDefinition, inventory);

        List<Product> products = recommendations.stream()
                .map(ProductRecommendation::toProduct)
                .collect(Collectors.toList());

        return getAvailabilityAndPrices(products, categoryDefinition, histories);
    }

    private List<AvailabilityAndPrice> getAvailabilityAndPrices(List<Product> products, CategoryDefinition categoryDefinition, Map<String, SellingPriceHistory> histories) {
        List<AvailabilityAndPrice> availabilityAndPrices = new ArrayList<>();

        for (Product product : products) {
            if (isAvailable(product, categoryDefinition.getAvailabilityDefinition())) {
                availabilityAndPrices.add(getAvailabilityAndPrice(product, categoryDefinition, histories));
            }
        }

        availabilityAndPrices = sortByPrice(availabilityAndPrices);
        if (categoryDefinition.hasGrouping()) {
            availabilityAndPrices = sortByLabel(categoryDefinition, availabilityAndPrices);
        }

        return availabilityAndPrices;
    }

    private AvailabilityAndPrice getAvailabilityAndPrice(Product p, CategoryDefinition c, Map<String, SellingPriceHistory> histories) {
        MatchedInventory matchedInventory = inventory.findByProduct(p);

        long grossPrice = pricing.calculateGrossPrice(p, c);
        int estimatedDeliveryDays = p.getEstimatedDeliveryDays() > 0
                ? p.getEstimatedDeliveryDays()
                : matchedInventory.getEstimatedDeliveryDays(grossPrice);
        long totalAvailableQty = matchedInventory.getTotalAvailableQty(grossPrice);

        SellingPriceHistory history = histories.get(p.getPimId());
        long lowest30DaysPrice = history != null ? history.getLowestPriceOtherThan(grossPrice) : grossPrice;

        return new AvailabilityAndPrice(
                p.getPimId(),
                p.getEan(),
                p.getManufacturerCode(),
                p.getBrand(),
                p.getLabel(),
                p.getName(),
                p.getCategory(),
                grossPrice,
                totalAvailableQty,
                estimatedDeliveryDays,
                lowest30DaysPrice
        );
    }

    private List<AvailabilityAndPrice> sortByPrice(List<AvailabilityAndPrice> availabilityAndPrices) {
        return availabilityAndPrices
                .stream()
                .sorted(Comparator.comparing(AvailabilityAndPrice::getPrice))
                .collect(Collectors.toList());
    }

    private List<AvailabilityAndPrice> sortByLabel(CategoryDefinition categoryDefinition, List<AvailabilityAndPrice> availabilityAndPrices) {
        List<AvailabilityAndPrice> sorted = new LinkedList<>();

        for (String label : categoryDefinition.getGroupingOrder()) {
            List<AvailabilityAndPrice> filtered = availabilityAndPrices.stream()
                    .filter(ap -> ap.getLabel().equals(label))
                    .collect(Collectors.toList());

            sorted.addAll(filtered);
        }

        return sorted;
    }

    private boolean isAvailable(Product product, AvailabilityDefinition availabilityDefinition) {
        if (product.hasAvailabilityType(ProductAvailabilityType.AlwaysAvailable) || product.hasAvailabilityType(ProductAvailabilityType.AlwaysAvailableFree)) {
            return true;
        }

        MatchedInventory matchedInventory = inventory.findByProduct(product);
        if (matchedInventory.hasOffersFrom(SupplierRegistry.WAREHOUSE)) {
            return true;
        }

        return matchedInventory.hasOffersFromMultipleSuppliers(availabilityDefinition.getMinNumberOfProviders())
                && matchedInventory.hasTotalMinQty(availabilityDefinition.getTotalMinQty());
    }
}
