package pl.commercelink.warehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.pricelist.RollingPriceAggregate;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.*;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DealHunter {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private RollingPriceAggregateRepository rollingPriceAggregateRepository;

    @Autowired
    private ProductRecommendationEngine recommendationEngine;

    @Autowired
    private Inventory inventory;

    public List<OrderItem> find(String storeId) {
        Map<String, RollingPriceAggregate> priceAggregates = rollingPriceAggregateRepository.loadAll();
        Set<String> seen = new HashSet<>();
        List<OrderItem> orderItems = new LinkedList<>();

        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(storeId);

        for (ProductCatalog productCatalog : productCatalogRepository.findAll(storeId)) {
            for (CategoryDefinition categoryDefinition : productCatalog.getCategories()) {
                List<Product> products;

                if (categoryDefinition.hasType(CategoryDefinitionType.Dynamic)) {
                    products = recommendationEngine.getRecommendations(categoryDefinition, enabledInventory).stream()
                            .filter(ProductRecommendation::hasPimId)
                            .map(ProductRecommendation::toProduct)
                            .collect(Collectors.toList());
                } else {
                    products = productRepository.findAll(categoryDefinition.getCategoryId());
                }

                for (Product product : products) {
                    if (product.getPimId() == null || product.getPimId().isEmpty()) {
                        continue;
                    }

                    if (seen.contains(product.getManufacturerCode().toLowerCase())) {
                        continue;
                    }

                    RollingPriceAggregate aggregate = priceAggregates.get(product.getPimId());
                    if (aggregate == null || !aggregate.isHotDeal()) {
                        continue;
                    }

                    int dealPrice = toDealPriceGross(aggregate);
                    if (dealPrice <= 0) {
                        continue;
                    }

                    seen.add(product.getManufacturerCode().toLowerCase());
                    orderItems.add(new OrderItem(
                            null,
                            product.getCategory(),
                            product.getName(),
                            1,
                            dealPrice,
                            product.getManufacturerCode(),
                            false
                    ));
                }
            }
        }

        return orderItems;
    }

    private int toDealPriceGross(RollingPriceAggregate aggregate) {
        if (aggregate.getCurrentLowestPrice() > 0) {
            Price price = Price.fromNet(aggregate.getCurrentLowestPrice());
            return (int) Math.round(price.grossValue());
        }
        return 0;
    }

}
