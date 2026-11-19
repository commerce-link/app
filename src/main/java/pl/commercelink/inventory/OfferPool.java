package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The offers of one grouping run. Witnesses are fetched by the filter that needs them, never for an empty pool:
 * the global inventory is large. Witnesses are not memoised: every call of {@code witnesses().get()} flattens the whole
 * global inventory again, so a second filter that needs them should share one fetch.
 */
record OfferPool(List<InventoryItem> offers, Supplier<? extends Collection<InventoryItem>> witnesses,
                 SharedManufacturerCodes sharedCodes) {

    /** The same pool without the rejected offers; witnesses and shared codes stay those of the whole pool. */
    OfferPool without(OfferFilter.Rejected rejected) {
        if (rejected.isEmpty()) {
            return this;
        }
        return new OfferPool(offers.stream().filter(offer -> !rejected.contains(offer)).toList(), witnesses, sharedCodes);
    }
}
