package pl.commercelink.inventory.search;

/**
 * The one-line answer above the table. {@code lowestGross} is the unit price of the offer that wins on
 * delivered cost, {@code lowestDeliveredGross} the same offer with shipping added.
 */
public record PriceSummary(double lowestGross, double lowestDeliveredGross, double medianGross,
                           int pricedOffersInStock, long supplierQty, int warehouseInStockQty,
                           int warehouseInDeliveryQty, boolean lowestIsUncertainMatch) {

    // with fewer offers the median is just one of the listed prices or the mean of two, which says nothing new
    static final int MEDIAN_MIN_OFFERS = 4;

    public boolean hasLowestPrice() {
        return lowestGross > 0;
    }

    public boolean showsMedian() {
        return pricedOffersInStock >= MEDIAN_MIN_OFFERS;
    }

    /** Only worth a second figure when shipping actually moves the number. */
    public boolean showsDeliveredPrice() {
        return lowestDeliveredGross > lowestGross;
    }
}
