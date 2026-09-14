package pl.commercelink.inventory.search;

public record PriceSummary(double lowestGross, double medianGross, int offerCount, long supplierQty,
                           int warehouseInStockQty, int warehouseInDeliveryQty) {

    public boolean hasSupplierOffers() {
        return offerCount > 0;
    }
}
