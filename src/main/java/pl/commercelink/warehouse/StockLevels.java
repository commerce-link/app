package pl.commercelink.warehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pricelist.RollingPriceAggregate;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StockLevels {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCatalogRepository productCatalogRepository;

    @Autowired
    private Warehouse warehouse;

    @Autowired
    private RollingPriceAggregateRepository rollingPriceAggregateRepository;

    @Autowired
    private ProductRecommendationEngine recommendationEngine;

    @Autowired
    private Inventory inventory;

    public List<StockProductLevel> calculate(String storeId, String catalogId, String categoryId, RestockScope scope, boolean onlyMissingItems) {
        ProductCatalog catalog = productCatalogRepository.findById(storeId, catalogId);
        if (catalog == null) {
            return Collections.emptyList();
        }

        Map<String, RollingPriceAggregate> priceAggregates = rollingPriceAggregateRepository.loadAll();

        List<CategoryDefinition> categories = catalog.getCategories().stream()
                .filter(c -> !c.isService())
                .filter(c -> categoryId == null || categoryId.isEmpty() || categoryId.equals(c.getCategoryId()))
                .toList();

        InventoryView enabledInventory = inventory.withEnabledSuppliersOnly(storeId, SupplierScope.FULFILMENT);

        List<StockProductLevel> stockProductLevels = new LinkedList<>();

        for (CategoryDefinition category : categories) {
            List<Product> products = resolveProducts(category, scope, enabledInventory);

            for (Product product : products) {
                int expectedQty = product.getStockExpectedQty();
                if (scope == RestockScope.ExpectedStockQty && expectedQty <= 0) {
                    continue;
                }

                int restockPriceLowest = getRestockPriceLowest(product, priceAggregates);
                int restockPriceHotDeal = getRestockPriceHotDeal(product, priceAggregates);
                int restockPricePromo = getRestockPricePromo(product, priceAggregates);
                int restockPriceStandard = getRestockPriceStandard(product, priceAggregates);

                Optional<StockProductLevel> optional = stockProductLevels.stream()
                        .filter(s -> s.hasManufacturerCode(product.getManufacturerCode())).findFirst();

                if (optional.isPresent()) {
                    StockProductLevel spl = optional.get();
                    spl.setRestockPricePromo(Math.min(spl.getRestockPricePromo(), restockPricePromo));
                    spl.setRestockPriceStandard(Math.min(spl.getRestockPriceStandard(), restockPriceStandard));
                    spl.setRestockPriceLowest(Math.min(spl.getRestockPriceLowest(), restockPriceLowest));
                    spl.setRestockPriceHotDeal(Math.min(spl.getRestockPriceHotDeal(), restockPriceHotDeal));
                    spl.setExpectedQuantity(Math.max(spl.getExpectedQuantity(), expectedQty));
                } else {
                    StockProductLevel spl = new StockProductLevel(
                            category.getCategory(),
                            product.getManufacturerCode(),
                            product.getName(),
                            restockPricePromo,
                            restockPriceStandard,
                            expectedQty
                    );
                    spl.setRestockPriceLowest(restockPriceLowest);
                    spl.setRestockPriceHotDeal(restockPriceHotDeal);
                    stockProductLevels.add(spl);
                }
            }
        }

        if (scope == RestockScope.ExpectedStockQty) {
            for (StockProductLevel s : stockProductLevels) {
                List<WarehouseItemView> items = warehouse.stockQueryService(storeId)
                        .searchByMfns(storeId, Collections.singletonList(s.getManufacturerCode()));
                s.calculateStock(items);
            }
            return stockProductLevels.stream()
                    .filter(i -> !onlyMissingItems || i.isFullyMissing())
                    .sorted(Comparator.comparing(StockProductLevel::getCategory).thenComparing(StockProductLevel::getName))
                    .collect(Collectors.toList());
        }

        return stockProductLevels.stream()
                .sorted(Comparator.comparing(StockProductLevel::getCategory).thenComparing(StockProductLevel::getName))
                .collect(Collectors.toList());
    }

    private List<Product> resolveProducts(CategoryDefinition category, RestockScope scope, InventoryView enabledInventory) {
        if (category.hasType(CategoryDefinitionType.Dynamic)) {
            if (scope == RestockScope.ExpectedStockQty) {
                return Collections.emptyList();
            }
            return recommendationEngine.getRecommendations(category, enabledInventory).stream()
                    .filter(ProductRecommendation::hasPimId)
                    .map(ProductRecommendation::toProduct)
                    .collect(Collectors.toList());
        }
        return productRepository.findAll(category.getCategoryId()).stream()
                .filter(p -> scope == RestockScope.WholeCatalog || p.getStockExpectedQty() > 0)
                .collect(Collectors.toList());
    }

    private int getRestockPricePromo(Product product, Map<String, RollingPriceAggregate> priceAggregates) {
        if (product.getRestockPricePromo() > 0) {
            return product.getRestockPricePromo();
        }

        if (product.getPimId() != null && !product.getPimId().isEmpty()) {
            RollingPriceAggregate aggregate = priceAggregates.get(product.getPimId());
            if (aggregate != null) {
                Price price = Price.fromNet(aggregate.getMedianLowestPrice30d());
                return (int) Math.round(price.grossValue());
            }
        }

        return 0;
    }

    private int getRestockPriceLowest(Product product, Map<String, RollingPriceAggregate> priceAggregates) {
        if (product.getPimId() != null && !product.getPimId().isEmpty()) {
            RollingPriceAggregate aggregate = priceAggregates.get(product.getPimId());
            if (aggregate != null && aggregate.isAtLowestPrice()) {
                Price price = Price.fromNet(aggregate.getCurrentLowestPrice());
                return (int) Math.round(price.grossValue());
            }
        }
        return 0;
    }

    private int getRestockPriceHotDeal(Product product, Map<String, RollingPriceAggregate> priceAggregates) {
        if (product.getPimId() != null && !product.getPimId().isEmpty()) {
            RollingPriceAggregate aggregate = priceAggregates.get(product.getPimId());
            if (aggregate != null && aggregate.isHotDeal()) {
                Price price = Price.fromNet(aggregate.getCurrentLowestPrice());
                return (int) Math.round(price.grossValue());
            }
        }
        return 0;
    }

    private int getRestockPriceStandard(Product product, Map<String, RollingPriceAggregate> priceAggregates) {
        if (product.getRestockPriceStandard() > 0) {
            return product.getRestockPriceStandard();
        }

        if (product.getPimId() != null && !product.getPimId().isEmpty()) {
            RollingPriceAggregate aggregate = priceAggregates.get(product.getPimId());
            if (aggregate != null) {
                Price price = Price.fromNet(aggregate.getMedianMedianPrice30d());
                return (int) Math.round(price.grossValue());
            }
        }

        return 0;
    }

}
