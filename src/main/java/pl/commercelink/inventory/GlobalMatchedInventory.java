package pl.commercelink.inventory;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GlobalMatchedInventory {

    private volatile Collection<MatchedInventory> matched = new LinkedList<>();
    private volatile Map<String, MatchedInventory> byEan = new HashMap<>();
    private volatile Map<String, MatchedInventory> byMfn = new HashMap<>();
    private volatile Map<String, MatchedInventory> byId = new HashMap<>();

    public void replace(Collection<MatchedInventory> matched) {
        Map<String, MatchedInventory> ean = new HashMap<>();
        Map<String, MatchedInventory> mfn = new HashMap<>();
        Map<String, MatchedInventory> id = new HashMap<>();
        for (MatchedInventory group : matched) {
            group.getEans().forEach(e -> ean.put(e, group));
            group.getMfnCodes().forEach(m -> mfn.put(m, group));
            if (group.getInventoryKey() != null && group.getInventoryKey().getId() != null) {
                id.put(group.getInventoryKey().getId(), group);
            }
        }
        this.byEan = ean;
        this.byMfn = mfn;
        this.byId = id;
        this.matched = matched;
    }

    public Collection<MatchedInventory> candidatesFor(InventoryKey key) {
        Set<MatchedInventory> result = new LinkedHashSet<>();
        if (key.getId() != null) {
            MatchedInventory byIdMatch = byId.get(key.getId());
            if (byIdMatch != null) {
                result.add(byIdMatch);
            }
        }
        for (String ean : key.getProductEans()) {
            MatchedInventory match = byEan.get(ean);
            if (match != null) {
                result.add(match);
            }
        }
        for (String mfn : key.getProductCodes()) {
            MatchedInventory match = byMfn.get(mfn);
            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    public Collection<MatchedInventory> all() {
        return matched;
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
