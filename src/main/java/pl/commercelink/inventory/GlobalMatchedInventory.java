package pl.commercelink.inventory;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GlobalMatchedInventory {

    private volatile Collection<MatchedInventory> matched = new LinkedList<>();
    private volatile GlobalInventoryIndex index;

    public synchronized void replace(Collection<MatchedInventory> matched) {
        this.matched = matched;
        this.index = null;
    }

    public Collection<MatchedInventory> all() {
        return matched;
    }

    public GlobalInventoryIndex index() {
        GlobalInventoryIndex current = index;
        if (current == null) {
            synchronized (this) {
                current = index;
                if (current == null) {
                    current = GlobalInventoryIndex.of(matched);
                    index = current;
                }
            }
        }
        return current;
    }

    public List<InventoryItem> allItems() {
        return matched.stream()
                .flatMap(i -> i.getInventoryItems().stream())
                .collect(Collectors.toList());
    }

    public int size() {
        return matched.size();
    }
}
