package pl.commercelink.inventory.search;

public record PriceSummary(double lowestNet, double lowestGross, String lowestSupplierLabel, double medianNet,
                           double medianGross, int offerCount, long supplierQty, int suppliersWithStock, int supplierCount,
                           int warehouseInStockQty, int warehouseInDeliveryQty) {

    public boolean hasSupplierOffers() {
        return offerCount > 0;
    }
}
