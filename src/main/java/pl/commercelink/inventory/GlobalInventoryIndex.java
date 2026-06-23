package pl.commercelink.inventory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class GlobalInventoryIndex {

    private final List<MatchedInventory> groups;
    private final Map<String, Set<MatchedInventory>> byId;
    private final Map<String, Set<MatchedInventory>> byEan;
    private final Map<String, Set<MatchedInventory>> byProductCode;

    private GlobalInventoryIndex(List<MatchedInventory> groups,
                                 Map<String, Set<MatchedInventory>> byId,
                                 Map<String, Set<MatchedInventory>> byEan,
                                 Map<String, Set<MatchedInventory>> byProductCode) {
        this.groups = groups;
        this.byId = byId;
        this.byEan = byEan;
        this.byProductCode = byProductCode;
    }

    static GlobalInventoryIndex of(Collection<MatchedInventory> groups) {
        Map<String, Set<MatchedInventory>> byId = new HashMap<>();
        Map<String, Set<MatchedInventory>> byEan = new HashMap<>();
        Map<String, Set<MatchedInventory>> byProductCode = new HashMap<>();
        for (MatchedInventory group : groups) {
            InventoryKey key = group.getInventoryKey();
            if (key.getId() != null) {
                byId.computeIfAbsent(key.getId(), k -> new LinkedHashSet<>()).add(group);
            }
            key.getProductEans().forEach(ean -> byEan.computeIfAbsent(ean, k -> new LinkedHashSet<>()).add(group));
            key.getProductCodes().forEach(code -> byProductCode.computeIfAbsent(code, k -> new LinkedHashSet<>()).add(group));
        }
        return new GlobalInventoryIndex(List.copyOf(groups), byId, byEan, byProductCode);
    }

    List<MatchedInventory> all() {
        return groups;
    }

    List<MatchedInventory> findMatching(InventoryKey key) {
        Set<MatchedInventory> matches = new LinkedHashSet<>();
        if (key.getId() != null) {
            matches.addAll(byId.getOrDefault(key.getId(), Set.of()));
        }
        key.getProductEans().forEach(ean -> matches.addAll(byEan.getOrDefault(ean, Set.of())));
        key.getProductCodes().forEach(code -> matches.addAll(byProductCode.getOrDefault(code, Set.of())));
        return List.copyOf(matches);
    }

    boolean contains(InventoryKey key) {
        if (key.getId() != null && byId.containsKey(key.getId())) {
            return true;
        }
        return key.getProductEans().stream().anyMatch(byEan::containsKey)
                || key.getProductCodes().stream().anyMatch(byProductCode::containsKey);
    }
}
