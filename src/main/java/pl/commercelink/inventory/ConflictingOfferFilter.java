package pl.commercelink.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rejects the offers whose EAN and whose brand with manufacturer code point to different products. */
final class ConflictingOfferFilter implements OfferFilter {

    // Borrows the logger of InventoryAutoDiscovery: the warning was logged under that name before the rules were split out, and log names must not change.
    private static final Logger log = LoggerFactory.getLogger(InventoryAutoDiscovery.class);

    @Override
    public Rejected reject(OfferPool pool) {
        ConflictingOffers conflicting = ConflictingOffers.of(pool.offers(), pool.witnesses().get(), pool.sharedCodes());
        if (conflicting.size() > 0) {
            log.warn("Left {} offers out of grouping: their EAN and their brand with manufacturer code point to different products", conflicting.size());
        }
        return conflicting;
    }
}
