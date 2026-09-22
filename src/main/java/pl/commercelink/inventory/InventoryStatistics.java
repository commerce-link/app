package pl.commercelink.inventory;

import java.util.Map;

public record InventoryStatistics(int distinctProducts, int productsInStock, Map<String, SupplierOfferStats> bySupplier) {

    public static final InventoryStatistics EMPTY = new InventoryStatistics(0, 0, Map.of());

    public int inStockPercent() {
        return distinctProducts == 0 ? 0 : Math.round(productsInStock * 100f / distinctProducts);
    }

    public int productsOf(String supplier) {
        SupplierOfferStats stats = bySupplier.get(supplier);
        return stats == null ? 0 : stats.products();
    }
}
