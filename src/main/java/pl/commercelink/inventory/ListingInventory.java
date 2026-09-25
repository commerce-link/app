package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.stream.Stream;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ListingInventory {

    private final InventoryIndex globalIndex;
    private final InventoryIndex ownIndex;

    Stream<MatchedInventory> groups() {
        Stream<MatchedInventory> globalGroups = globalIndex.all().stream();
        if (ownIndex.all().isEmpty()) {
            return globalGroups;
        }
        Stream<MatchedInventory> ownOnlyGroups = ownIndex.all().stream()
                .filter(group -> !globalIndex.contains(group.getInventoryKey()));
        return Stream.concat(globalGroups, ownOnlyGroups);
    }

    Stream<InventoryKey> keys() {
        return groups().map(MatchedInventory::getInventoryKey);
    }
}
