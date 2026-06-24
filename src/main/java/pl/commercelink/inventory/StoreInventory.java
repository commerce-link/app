package pl.commercelink.inventory;

import java.time.LocalDateTime;
import java.util.Collection;

public record StoreInventory(InventoryIndex index, LocalDateTime builtAt) {

    public Collection<MatchedInventory> items() {
        return index.all();
    }
}
