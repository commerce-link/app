package pl.commercelink.localdev;

import java.util.List;

public record CatalogSeedRow(
        String pimId,
        String ean,
        String mfn,
        String brand,
        String label,
        String name,
        String category,
        int priceGross,
        int qty,
        int estimatedDeliveryDays,
        int lowest30DaysPrice,
        List<String> suppliers,
        boolean inWarehouse,
        boolean inCatalog) {

    public boolean soldBy(String supplier) {
        return suppliers.contains(supplier);
    }
}
