package pl.commercelink.inventory;

import java.util.Collection;
import java.util.List;

class OwnInventorySource extends GroupInventorySource {

    private final Collection<MatchedInventory> ownInventory;

    OwnInventorySource(Collection<MatchedInventory> ownInventory) {
        this.ownInventory = ownInventory;
    }

    @Override
    List<MatchedInventory> findCandidates(InventoryKey lookupKey) {
        return ownInventory.stream()
                .filter(group -> group.matches(lookupKey))
                .toList();
    }
}
