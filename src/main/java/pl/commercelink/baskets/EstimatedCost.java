package pl.commercelink.baskets;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The cost assumed for an offer item the operator gave no cost: halfway between the suppliers' lowest and median gross
 * cost, rounded half up to grosze (client 2026-10-27: buying at the median is rare, at the lowest not always possible).
 * The offer page shows it for an item without a cost, and an item added from the inventory outside the price list
 * stores it as its cost.
 */
public final class EstimatedCost {

    private EstimatedCost() {
    }

    /** 0 when either cost is unknown. */
    public static double between(double lowestGross, double medianGross) {
        if (lowestGross <= 0 || medianGross <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(lowestGross).add(BigDecimal.valueOf(medianGross))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
