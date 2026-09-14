package pl.commercelink.inventory.search;

public record PriceSummary(double lowestGross, double medianGross, int pricedOffersInStock, long supplierQty,
                           int warehouseInStockQty, int warehouseInDeliveryQty) {

    // with fewer offers the median is just one of the listed prices or the mean of two, which says nothing new
    static final int MEDIAN_MIN_OFFERS = 4;

    public boolean hasLowestPrice() {
        return lowestGross > 0;
    }

    public boolean showsMedian() {
        return pricedOffersInStock >= MEDIAN_MIN_OFFERS;
    }
}
