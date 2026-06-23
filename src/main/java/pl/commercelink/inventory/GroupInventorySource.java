package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.List;

abstract class GroupInventorySource implements InventorySource {

    @Override
    public void mergeInto(MatchedInventory result, InventoryKey lookupKey) {
        MatchedInventory best = selectBest(findCandidates(lookupKey), lookupKey);
        if (best == null) {
            return;
        }
        result.getInventoryKey().merge(best.getInventoryKey());
        for (InventoryItem item : best.getInventoryItems()) {
            if (accepts(item)) {
                result.addAlternativeInventoryItem(item);
            }
        }
    }

    abstract List<MatchedInventory> findCandidates(InventoryKey lookupKey);

    boolean accepts(InventoryItem item) {
        return true;
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
