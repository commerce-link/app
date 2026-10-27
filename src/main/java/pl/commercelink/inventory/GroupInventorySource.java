package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.List;
import java.util.function.Predicate;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class GroupInventorySource implements InventorySource {

    private final InventoryIndex index;
    private final Predicate<String> enabledSupplier;
    private final StoreEntryKeys storeEntries;

    static GroupInventorySource global(InventoryIndex index, Predicate<String> enabledSupplier) {
        return global(index, enabledSupplier, StoreEntryKeys.none());
    }

    /**
     * A global source that never merges a group of another PIM entry into the row of a store entry: a global feed
     * row may share a code with the store entry while belonging to a different product (spec 4.3, c-own).
     */
    static GroupInventorySource global(InventoryIndex index, Predicate<String> enabledSupplier, StoreEntryKeys storeEntries) {
        return new GroupInventorySource(index, enabledSupplier, storeEntries);
    }

    static GroupInventorySource own(InventoryIndex index) {
        return own(index, supplier -> true);
    }

    static GroupInventorySource own(InventoryIndex index, Predicate<String> enabledSupplier) {
        return new GroupInventorySource(index, enabledSupplier, StoreEntryKeys.none());
    }

    @Override
    public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
        MatchedInventory best = selectBest(candidates(lookupKey), lookupKey);
        if (best == null) {
            return;
        }
        result.getInventoryKey().merge(best.getInventoryKey());
        for (InventoryItem item : best.getInventoryItems()) {
            if (enabledSupplier.test(item.supplier())) {
                result.addAlternativeInventoryItem(item);
            }
        }
    }

    private List<MatchedInventory> candidates(InventoryKey lookupKey) {
        List<MatchedInventory> matches = index.findMatching(lookupKey);
        if (!InventoryIndex.isStoreEntryKey(lookupKey, storeEntries)) {
            return matches;
        }
        return matches.stream()
                .filter(group -> InventoryIndex.sameOrNoEntry(group.getInventoryKey(), lookupKey.getId(),
                        storeEntries.sourceOf(lookupKey.getId())))
                .toList();
    }

    // prioritize lookup by mfn as it's more reliable
    private MatchedInventory selectBest(List<MatchedInventory> candidates, InventoryKey lookupKey) {
        List<MatchedInventory> matchedByMfn = candidates.stream()
                .filter(group -> group.matchedByMfn(lookupKey))
                .toList();
        MatchedInventory best = largest(matchedByMfn);
        return best != null ? best : largest(candidates);
    }

    private MatchedInventory largest(List<MatchedInventory> groups) {
        MatchedInventory best = null;
        int bestSize = 0;
        for (MatchedInventory group : groups) {
            if (group.size() > bestSize) {
                bestSize = group.size();
                best = group;
            }
        }
        return best;
    }
}
