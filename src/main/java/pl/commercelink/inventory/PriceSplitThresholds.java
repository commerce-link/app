package pl.commercelink.inventory;

/**
 * Price thresholds shared by every price-based split of a matched group, so that the price control of groups
 * with several manufacturer codes and {@link SingleCodePriceSplit} cannot drift apart.
 */
final class PriceSplitThresholds {

    /** Prices further apart than this factor are treated as different products. */
    static final double MAX_RATIO = 2.0;

    /** Below this split price a price is too small to tell products apart. */
    static final double MIN_SPLIT_PRICE = 50.0;

    private PriceSplitThresholds() {
    }

    /** The price that separates the cheap part of a group from the expensive one. */
    static double splitPriceOf(double highestAveragePrice) {
        return highestAveragePrice / MAX_RATIO;
    }

    static boolean isTooLowToSplit(double splitPrice) {
        return splitPrice < MIN_SPLIT_PRICE;
    }

    /**
     * Whether two average prices are further apart than MAX_RATIO; a non-positive or NaN price is always too far
     * (fail-safe: NaN fails every comparison, so without the explicit check it would count as close).
     */
    static boolean areTooFarApart(double averagePrice, double otherAveragePrice) {
        if (Double.isNaN(averagePrice) || Double.isNaN(otherAveragePrice) || averagePrice <= 0 || otherAveragePrice <= 0) {
            return true;
        }
        return Math.max(averagePrice, otherAveragePrice) / Math.min(averagePrice, otherAveragePrice) > MAX_RATIO;
    }
}
