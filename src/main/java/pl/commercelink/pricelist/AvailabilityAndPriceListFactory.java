package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;

@Component
public class AvailabilityAndPriceListFactory {

    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final ProductRecommendationEngine recommendationEngine;
    private final RollingPriceAggregateRepository rollingPriceAggregateRepository;
    private final SellingPriceHistoryRepository sellingPriceHistoryRepository;

    @Autowired
    public AvailabilityAndPriceListFactory(
            ProductCatalogRepository productCatalogRepository,
            ProductRepository productRepository,
            ProductRecommendationEngine recommendationEngine,
            RollingPriceAggregateRepository rollingPriceAggregateRepository,
            SellingPriceHistoryRepository sellingPriceHistoryRepository) {
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.recommendationEngine = recommendationEngine;
        this.rollingPriceAggregateRepository = rollingPriceAggregateRepository;
        this.sellingPriceHistoryRepository = sellingPriceHistoryRepository;
    }

    public AvailabilityAndPriceList create(InventoryView inventory) {
        return new AvailabilityAndPriceList(
                inventory,
                productCatalogRepository,
                productRepository,
                recommendationEngine,
                rollingPriceAggregateRepository,
                sellingPriceHistoryRepository
        );
    }
}
