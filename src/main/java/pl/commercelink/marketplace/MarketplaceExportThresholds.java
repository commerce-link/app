package pl.commercelink.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceExportThresholds(
        long totalQuantity,
        int minTotalQuantity,
        long distributorsWithMinQuantity,
        int minNumOfDistributors,
        int minQuantityPerDistributor,
        long warehouseQuantity,
        int minWarehouseQuantity) {

    public static MarketplaceExportThresholds warehouse(long warehouseQuantity, int minWarehouseQuantity) {
        return new MarketplaceExportThresholds(0L, 0, 0L, 0, 0, warehouseQuantity, minWarehouseQuantity);
    }

    public static MarketplaceExportThresholds distributors(long totalQuantity,
                                                          int minTotalQuantity,
                                                          long distributorsWithMinQuantity,
                                                          int minNumOfDistributors,
                                                          int minQuantityPerDistributor) {
        return new MarketplaceExportThresholds(
                totalQuantity, minTotalQuantity, distributorsWithMinQuantity, minNumOfDistributors, minQuantityPerDistributor, 0L, 0);
    }
}
