package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

/** A rule that rejects offers before grouping: a rejected offer is left out of every group. */
interface OfferFilter {

    Rejected reject(OfferPool pool);

    /** Offers left out of every group, compared by identity. */
    interface Rejected {
        boolean isEmpty();

        boolean contains(InventoryItem offer);
    }
}
