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

    static GroupInventorySource global(InventoryIndex index, Predicate<String> enabledSupplier) {
        return new GroupInventorySource(index, enabledSupplier);
    }

    static GroupInventorySource own(InventoryIndex index) {
        return own(index, supplier -> true);
    }

    static GroupInventorySource own(InventoryIndex index, Predicate<String> enabledSupplier) {
        return new GroupInventorySource(index, enabledSupplier);
    }

    @Override
    public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
        MatchedInventory best = selectBest(index.findMatching(lookupKey), lookupKey);
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
