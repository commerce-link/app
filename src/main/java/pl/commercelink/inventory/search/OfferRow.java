package pl.commercelink.inventory.search;

import pl.commercelink.stores.ConnectionMode;

/**
 * One supplier's offer for the searched product. Price alone does not decide where to buy, so the row
 * also carries what the supplier charges to ship one unit and how long the whole thing takes.
 */
public record OfferRow(String supplier, String supplierLabel, ConnectionMode mode, String productEan, String productCode,
                       double netPrice, double grossPrice, double deliveryNet, double freeDeliveryFrom,
                       boolean deliveryKnown, int leadTimeDays, int qty, boolean cheapest, boolean sharesLabel,
                       CodeMatch codeMatch) {

    public boolean hasStock() {
        return qty > 0;
    }

    public boolean hasPrice() {
        return grossPrice > 0;
    }

    public boolean freeDelivery() {
        return deliveryKnown && deliveryNet <= 0;
    }

    public boolean hasDeliveryCost() {
        return deliveryKnown && deliveryNet > 0;
    }

    /** Only worth showing when free shipping is actually reachable, not at the catch-all placeholder. */
    public boolean hasFreeDeliveryFrom() {
        return hasDeliveryCost() && freeDeliveryFrom > 0;
    }

    public boolean hasLeadTime() {
        return deliveryKnown && leadTimeDays > 0;
    }

    /**
     * Another offer in the same result carries this label, so the name alone no longer identifies the row.
     * The codes it was matched on are then the only way to tell the two apart.
     */
    public boolean needsOwnCodes() {
        return sharesLabel && codeMatch == CodeMatch.SAME;
    }

    /**
     * Net cost of a single unit on the doorstep. Two offers a few zloty apart are decided by shipping,
     * so this, not the bare price, is what the cheapest marker and the default order compare.
     */
    public double totalNet() {
        return hasPrice() ? netPrice + deliveryNet : 0;
    }
}
