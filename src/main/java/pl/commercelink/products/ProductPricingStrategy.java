package pl.commercelink.products;

import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.pricelist.RollingPriceAggregate;
import pl.commercelink.warehouse.StockLevel;

import java.util.Map;

public class ProductPricingStrategy {

    private static final double STOP_LOSS_THRESHOLD = 0.20;

    private final InventoryView inventory;
    private final Map<String, RollingPriceAggregate> rollingPriceAggregates;

    public ProductPricingStrategy(InventoryView inventory, Map<String, RollingPriceAggregate> rollingPriceAggregates) {
        this.inventory = inventory;
        this.rollingPriceAggregates = rollingPriceAggregates;
    }

    public long calculateGrossPrice(Product product, CategoryDefinition categoryDefinition) {
        if (product.hasAvailabilityType(ProductAvailabilityType.AlwaysAvailableFree)) {
            return 0;
        }

        if (product.hasAvailabilityType(ProductAvailabilityType.AlwaysAvailable)) {
            return calculatePriceForAlwaysAvailableProducts(product);
        }

        return calculatePriceForSupplyBasedProducts(product, categoryDefinition);
    }

    private int calculatePriceForAlwaysAvailableProducts(Product product) {
        int price = product.getSuggestedRetailPrice();
        if (price <= 0) {
            throw new RuntimeException("Products with availability type 'AlwaysAvailable' must have a price set");
        }
        return price;
    }

    private long calculatePriceForSupplyBasedProducts(Product product, CategoryDefinition categoryDefinition) {
        MatchedInventory matchedInventory = inventory.findByProduct(product);
        PriceDefinition priceDefinition = categoryDefinition.findPriceDefinition(product.getPricingGroup());
        StockDefinition stockDefinition = categoryDefinition.getStockDefinition();

        long ourGrossPrice = calculateGrossPrice(product, matchedInventory, priceDefinition, stockDefinition);

        return Math.max(ourGrossPrice, product.getSuggestedRetailPrice());
    }

    private long calculateGrossPrice(Product product, MatchedInventory matchedInventory, PriceDefinition priceDefinition, StockDefinition stockDefinition) {
        StockLevel stockLevel = stockDefinition.getStockLevel(matchedInventory);
        double lowestPrice = matchedInventory.getLowestPrice(true, null).grossValue();
        double medianPrice = matchedInventory.getMedianPrice().grossValue();

        double baseItemPrice;
        if (stockLevel == StockLevel.High) {
            baseItemPrice = lowestPrice;
        } else if (stockLevel == StockLevel.Medium) {
            baseItemPrice = (lowestPrice + medianPrice) / 2.0;
        } else {
            baseItemPrice = medianPrice;
        }

        if (!matchedInventory.canBeFulfilledFromWarehouseAtPricePoint(baseItemPrice)) {
            baseItemPrice = applyStopLoss(product, baseItemPrice);
        }

        long ourPrice = round(baseItemPrice * priceDefinition.getMultiplier(), 9);
        long lowestMarketPrice = (long) matchedInventory.getLowestPrice(SupplierType.Retailer).grossValue();
        long lowestDistributorPrice = (long) matchedInventory.getLowestPrice(SupplierType.Distributor).grossValue();

        if (stockLevel == StockLevel.Critical) {
            ourPrice += priceDefinition.getCriticalStockPriceAdjustment();
        } else if (stockLevel == StockLevel.Low) {
            ourPrice += priceDefinition.getLowStockPriceAdjustment();
        } else if (stockLevel == StockLevel.Medium) {
            ourPrice += priceDefinition.getMediumStockPriceAdjustment();
        }

        long ourHypotheticalProfit = calculateOurProfit(ourPrice, lowestMarketPrice, lowestDistributorPrice);
        if (ourHypotheticalProfit < priceDefinition.getMinProfit()) {
            long priceAdjustment = priceDefinition.getMinProfit() - ourHypotheticalProfit;
            ourPrice = ourPrice + priceAdjustment;
        }

        return roundToNearestNine(ourPrice);
    }

    private long roundToNearestNine(long ourPrice) {
        return ((ourPrice - 1) / 10) * 10 + 9;
    }

    private long calculateOurProfit(long ourPrice, long lowestMarketPrice, long lowestProviderPrice) {
        if (lowestMarketPrice == 0) {
            return ourPrice - lowestProviderPrice;
        } else if (lowestProviderPrice == 0 || lowestMarketPrice < lowestProviderPrice) {
            return ourPrice - lowestMarketPrice;
        } else {
            return ourPrice - lowestProviderPrice;
        }
    }

    private long round(double value, int nearest) {
        return (long) Math.ceil(value / nearest) * nearest;
    }

    private double applyStopLoss(Product product, double currentBasePrice) {
        if (currentBasePrice == 0 || rollingPriceAggregates == null || rollingPriceAggregates.isEmpty()) {
            return currentBasePrice;
        }

        String pimId = product.getPimId();
        if (pimId == null || pimId.isEmpty()) {
            return currentBasePrice;
        }

        RollingPriceAggregate aggregate = rollingPriceAggregates.get(pimId);
        if (aggregate == null) {
            return currentBasePrice;
        }

        double medianLowestPriceNet30d = aggregate.getMedianLowestPrice30d();
        if (medianLowestPriceNet30d == 0) {
            return currentBasePrice;
        }

        double medianLowestPriceGross30d = Price.fromNet(medianLowestPriceNet30d).grossValue();
        double stopLossFloor = medianLowestPriceGross30d * (1 - STOP_LOSS_THRESHOLD);

        if (currentBasePrice < stopLossFloor) {
            return medianLowestPriceGross30d;
        }

        return currentBasePrice;
    }

}
