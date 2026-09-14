package pl.commercelink.inventory.search;

public record PriceSummary(double lowestGross, String lowestSupplierLabel, double medianGross, int offerCount,
                           long supplierQty, int suppliersWithStock, int supplierCount,
                           int warehouseInStockQty, int warehouseInDeliveryQty) {

    public boolean hasSupplierOffers() {
        return offerCount > 0;
    }
}
