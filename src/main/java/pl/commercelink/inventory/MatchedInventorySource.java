package pl.commercelink.inventory;

import java.util.Collection;

interface MatchedInventorySource {

    Collection<MatchedInventory> candidatesFor(InventoryKey key);

    Collection<MatchedInventory> all();
}
