package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;
import java.util.Optional;

/** A rule that looks at one finished group and either leaves it or replaces it with other offer sets. */
interface GroupRefiner {

    /**
     * @param offers the offers of one group that has offers of at least two suppliers
     * @return the offer sets that replace the group (a set may be empty, offers may be dropped),
     *         or empty when this rule leaves the group as it is
     */
    Optional<List<List<InventoryItem>>> refine(List<InventoryItem> offers);
}
