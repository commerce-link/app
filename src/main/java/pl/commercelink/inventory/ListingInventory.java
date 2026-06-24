package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.stream.Stream;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ListingInventory {

    private final InventoryIndex globalIndex;
    private final InventoryIndex ownIndex;

    Stream<InventoryKey> keys() {
        Stream<InventoryKey> globalKeys = globalIndex.all().stream().map(MatchedInventory::getInventoryKey);
        if (ownIndex.all().isEmpty()) {
            return globalKeys;
        }
        Stream<InventoryKey> ownOnlyKeys = ownIndex.all().stream()
                .map(MatchedInventory::getInventoryKey)
                .filter(key -> !globalIndex.contains(key));
        return Stream.concat(globalKeys, ownOnlyKeys);
    }
}
