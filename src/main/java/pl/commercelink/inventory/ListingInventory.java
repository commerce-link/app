package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.function.Predicate;
import java.util.stream.Stream;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ListingInventory {

    private final InventoryIndex globalIndex;
    private final InventoryIndex ownIndex;
    private final Predicate<String> isStoreEntry;

    Stream<InventoryKey> keys() {
        Stream<InventoryKey> globalKeys = globalIndex.all().stream().map(MatchedInventory::getInventoryKey);
        if (ownIndex.all().isEmpty()) {
            return globalKeys;
        }
        Stream<InventoryKey> ownOnlyKeys = ownIndex.all().stream()
                .map(MatchedInventory::getInventoryKey)
                .filter(key -> !globalIndex.covers(key, isStoreEntry));
        return Stream.concat(globalKeys, ownOnlyKeys);
    }
}
