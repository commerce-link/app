package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;

@Component
public class AvailabilityAndPriceListFactory {

    private final PimCatalog pimCatalog;
    private final ProductCatalogRepository productCatalogRepository;
    private final ProductRepository productRepository;
    private final ProductRecommendationEngine recommendationEngine;
    private final RollingPriceAggregateRepository rollingPriceAggregateRepository;
    private final SellingPriceHistoryRepository sellingPriceHistoryRepository;
    private final StorePimAliases aliases;

    @Autowired
    public AvailabilityAndPriceListFactory(
            PimCatalog pimCatalog,
            ProductCatalogRepository productCatalogRepository,
            ProductRepository productRepository,
            ProductRecommendationEngine recommendationEngine,
            RollingPriceAggregateRepository rollingPriceAggregateRepository,
            SellingPriceHistoryRepository sellingPriceHistoryRepository,
            StorePimAliases aliases) {
        this.pimCatalog = pimCatalog;
        this.productCatalogRepository = productCatalogRepository;
        this.productRepository = productRepository;
        this.recommendationEngine = recommendationEngine;
        this.rollingPriceAggregateRepository = rollingPriceAggregateRepository;
        this.sellingPriceHistoryRepository = sellingPriceHistoryRepository;
        this.aliases = aliases;
    }

    public AvailabilityAndPriceList create(InventoryView inventory, String storeId) {
        return new AvailabilityAndPriceList(
                inventory,
                pimCatalog,
                productCatalogRepository,
                productRepository,
                recommendationEngine,
                rollingPriceAggregateRepository,
                sellingPriceHistoryRepository,
                aliases,
                storeId
        );
    }
}
