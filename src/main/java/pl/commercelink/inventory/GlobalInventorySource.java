package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.List;
import java.util.function.Predicate;

class GlobalInventorySource extends GroupInventorySource {

    private final GlobalInventoryIndex index;
    private final Predicate<String> enabledSupplier;

    GlobalInventorySource(GlobalInventoryIndex index, Predicate<String> enabledSupplier) {
        this.index = index;
        this.enabledSupplier = enabledSupplier;
    }

    @Override
    List<MatchedInventory> findCandidates(InventoryKey lookupKey) {
        return index.findMatching(lookupKey);
    }

    @Override
    boolean accepts(InventoryItem item) {
        return enabledSupplier.test(item.supplier());
    }
}
