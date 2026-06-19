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

    private record Index(Collection<MatchedInventory> matched,
                         Map<String, MatchedInventory> byEan,
                         Map<String, MatchedInventory> byMfn,
                         Map<String, MatchedInventory> byId) {
    }

    private volatile Index index = new Index(new LinkedList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());

    public void replace(Collection<MatchedInventory> matched) {
        Map<String, MatchedInventory> byEan = new HashMap<>();
        Map<String, MatchedInventory> byMfn = new HashMap<>();
        Map<String, MatchedInventory> byId = new HashMap<>();
        for (MatchedInventory group : matched) {
            group.getEans().forEach(ean -> byEan.put(ean, group));
            group.getMfnCodes().forEach(mfn -> byMfn.put(mfn, group));
            if (group.getInventoryKey() != null && group.getInventoryKey().getId() != null) {
                byId.put(group.getInventoryKey().getId(), group);
            }
        }
        this.index = new Index(matched, byEan, byMfn, byId);
    }

    public Collection<MatchedInventory> candidatesFor(InventoryKey key) {
        Index current = this.index;
        Set<MatchedInventory> result = new LinkedHashSet<>();
        if (key.getId() != null) {
            MatchedInventory byIdMatch = current.byId().get(key.getId());
            if (byIdMatch != null) {
                result.add(byIdMatch);
            }
        }
        for (String ean : key.getProductEans()) {
            MatchedInventory match = current.byEan().get(ean);
            if (match != null) {
                result.add(match);
            }
        }
        for (String mfn : key.getProductCodes()) {
            MatchedInventory match = current.byMfn().get(mfn);
            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    public Collection<MatchedInventory> all() {
        return index.matched();
    }

    public List<InventoryItem> allItems() {
        return index.matched().stream()
                .flatMap(i -> i.getInventoryItems().stream())
                .collect(Collectors.toList());
    }

    public int size() {
        return index.matched().size();
    }
}
