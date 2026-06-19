package pl.commercelink.inventory;

import java.util.Collection;
import java.util.stream.Collectors;

class SimpleMatchedInventorySource implements MatchedInventorySource {

    private final Collection<MatchedInventory> inventory;

    SimpleMatchedInventorySource(Collection<MatchedInventory> inventory) {
        this.inventory = inventory;
    }

    @Override
    public Collection<MatchedInventory> candidatesFor(InventoryKey key) {
        return inventory.stream()
                .filter(group -> group.matches(key))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<MatchedInventory> all() {
        return inventory;
    }
}
