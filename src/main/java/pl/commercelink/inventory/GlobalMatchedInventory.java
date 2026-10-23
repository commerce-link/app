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
    private volatile InventoryIndex index;
    private volatile long version;

    public synchronized void replace(Collection<MatchedInventory> matched) {
        this.matched = matched;
        this.index = null;
        this.version++;
    }

    public long version() {
        return version;
    }

    /** The groups, their index and the version of one reload, read together so they always belong to each other. */
    synchronized Generation generation() {
        return new Generation(version, matched, index());
    }

    record Generation(long version, Collection<MatchedInventory> all, InventoryIndex index) {
    }

    public Collection<MatchedInventory> all() {
        return matched;
    }

    public InventoryIndex index() {
        InventoryIndex current = index;
        if (current == null) {
            synchronized (this) {
                current = index;
                if (current == null) {
                    current = InventoryIndex.of(matched);
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
