package pl.commercelink.inventory;

import java.util.Collection;
import java.util.stream.Stream;

class ListingInventory {

    private final GlobalInventoryIndex globalIndex;
    private final Collection<MatchedInventory> own;

    ListingInventory(GlobalInventoryIndex globalIndex, Collection<MatchedInventory> own) {
        this.globalIndex = globalIndex;
        this.own = own;
    }

    Stream<InventoryKey> keys() {
        Stream<InventoryKey> globalKeys = globalIndex.all().stream().map(MatchedInventory::getInventoryKey);
        if (own.isEmpty()) {
            return globalKeys;
        }
        Stream<InventoryKey> ownOnlyKeys = own.stream()
                .map(MatchedInventory::getInventoryKey)
                .filter(key -> !globalIndex.contains(key));
        return Stream.concat(globalKeys, ownOnlyKeys);
    }
}
