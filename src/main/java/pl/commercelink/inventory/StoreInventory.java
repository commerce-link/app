package pl.commercelink.inventory;

import java.time.LocalDateTime;
import java.util.Collection;

public record StoreInventory(Collection<MatchedInventory> items, LocalDateTime builtAt) {
}
