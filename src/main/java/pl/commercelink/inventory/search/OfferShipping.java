package pl.commercelink.inventory.search;

import pl.commercelink.inventory.supplier.SupplierIdentity;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;

/**
 * What a supplier adds on top of the unit price: shipping for a single unit, the order value that makes
 * shipping free, and the days from ordering to arrival. Unknown suppliers report {@link #UNKNOWN} rather
 * than the registry's catch-all placeholder, because a made-up cost is worse than a blank cell.
 */
public record OfferShipping(double deliveryNet, double freeFrom, int totalDays, boolean known) {

    public static final OfferShipping UNKNOWN = new OfferShipping(0, 0, 0, false);

    /** The rest of the application quotes supplier shipping for Poland; browse and search must not disagree with it. */
    public static final String DESTINATION = "PL";

    /**
     * {@link ShippingCostPolicy.FlatRate} has no way of saying "never free", so adapters express that with
     * a threshold no order will reach — a million zloty, in half of them. Printing "free above 1 000 000 PLN"
     * would be noise on those suppliers, so a threshold beyond anything a basket reaches counts as none.
     */
    private static final double REACHABLE_THRESHOLD = 100_000;

    public static OfferShipping of(ShippingTerms terms, double unitNetPrice, int supplierLeadTimeDays) {
        double delivery = terms.costPolicy().calculate(unitNetPrice);
        // A threshold only helps if the buyer can reach it; the free-shipping rate reports none at all.
        double freeFrom = terms.costPolicy() instanceof ShippingCostPolicy.FlatRate rate
                && rate.cost() > 0 && rate.threshold() < REACHABLE_THRESHOLD
                ? rate.threshold()
                : 0;
        return new OfferShipping(delivery, freeFrom, supplierLeadTimeDays + terms.arrivalDays(), true);
    }

    /**
     * A supplier the registry does not know falls back to a placeholder policy there, so rather than
     * quote an invented cost the offer reports that shipping is simply unknown.
     */
    public static OfferShipping forItem(SupplierRegistry registry, InventoryItem item) {
        String supplier = item.supplier();
        if (!registry.exists(supplier) && !SupplierIdentity.isManual(supplier)) {
            return UNKNOWN;
        }
        return of(registry.get(supplier).shippingTermsFor(DESTINATION), item.netPrice(), item.leadTimeDays());
    }
}
