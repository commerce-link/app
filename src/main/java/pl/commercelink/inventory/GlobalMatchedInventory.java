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

    public void replace(Collection<MatchedInventory> matched) {
        this.matched = matched;
    }

    public Collection<MatchedInventory> all() {
        return matched;
    }

    public List<InventoryItem> allItems() {
        return matched.stream()
                .flatMap(i -> i.getInventoryItems().stream())
                .collect(Collectors.toList());
    }

    public List<InventoryItem> itemsForSuppliers(Collection<String> supplierNames) {
        return matched.stream()
                .flatMap(m -> m.getInventoryItems().stream())
                .filter(item -> supplierNames.contains(item.supplier()))
                .collect(Collectors.toList());
    }

    public int size() {
        return matched.size();
    }
}
