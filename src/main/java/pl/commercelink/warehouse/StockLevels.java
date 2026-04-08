package pl.commercelink.warehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pricelist.RollingPriceAggregate;
import pl.commercelink.pricelist.RollingPriceAggregateRepository;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
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

    public List<StockProductLevel> calculate(String storeId, boolean onlyMissingItems) {
        Map<String, RollingPriceAggregate> priceAggregates = rollingPriceAggregateRepository.loadAll();

        List<StockProductLevel> understockedItems = fetchStockProductLevelsForAllQualifiedProducts(storeId, priceAggregates);
        for (StockProductLevel i : understockedItems) {
            List<WarehouseItemView> items = warehouse.stockQueryService(storeId)
                    .searchByMfns(storeId, Collections.singletonList(i.getManufacturerCode()));
            i.calculateStock(items);
        }

        return understockedItems.stream()
                .filter(i -> !onlyMissingItems || i.isFullyMissing())
                .sorted(Comparator.comparing(StockProductLevel::getCategory))
                .collect(Collectors.toList());
    }

    private List<StockProductLevel> fetchStockProductLevelsForAllQualifiedProducts(String storeId, Map<String, RollingPriceAggregate> priceAggregates) {
        List<StockProductLevel> stockProductLevels = new LinkedList<>();

        for (ProductCatalog productCatalog : productCatalogRepository.findAll(storeId)) {
            List<Product> products = productRepository.findAllProductsThatQualifiesForRestock(productCatalog);

            for (Product product : products) {

                Optional<StockProductLevel> optional = stockProductLevels.stream()
                        .filter(s -> s.hasManufacturerCode(product.getManufacturerCode())).findFirst();

                // Get restock prices with RollingPriceAggregate fallback
                int restockPriceLowest = getRestockPriceLowest(product, priceAggregates);
                int restockPricePromo = getRestockPricePromo(product, priceAggregates);
                int restockPriceStandard = getRestockPriceStandard(product, priceAggregates);

                if (optional.isPresent()) {
                    StockProductLevel stockProductLevel = optional.get();

                    int minRestockPricePromo = Math.min(stockProductLevel.getRestockPricePromo(), restockPricePromo);
                    int minRestockPriceStandard = Math.min(stockProductLevel.getRestockPriceStandard(), restockPriceStandard);
                    int minRestockPriceLowest = Math.min(stockProductLevel.getRestockPriceLowest(), restockPriceLowest);
                    int expectedQuantity = Math.max(stockProductLevel.getExpectedQuantity(), product.getStockExpectedQty());

                    stockProductLevel.setRestockPricePromo(minRestockPricePromo);
                    stockProductLevel.setRestockPriceStandard(minRestockPriceStandard);
                    stockProductLevel.setRestockPriceLowest(minRestockPriceLowest);
                    stockProductLevel.setExpectedQuantity(expectedQuantity);
                } else {
                    StockProductLevel spl = new StockProductLevel(
                            product.getCategory(),
                            product.getManufacturerCode(),
                            product.getName(),
                            restockPricePromo,
                            restockPriceStandard,
                            product.getStockExpectedQty()
                    );
                    spl.setRestockPriceLowest(restockPriceLowest);
                    stockProductLevels.add(spl);

                }
            }
        }

        return stockProductLevels;
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
