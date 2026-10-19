package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class InventoryIndex {

    private final List<MatchedInventory> groups;
    private final Map<String, Set<MatchedInventory>> byId;
    private final Map<String, Set<MatchedInventory>> byEan;
    private final Map<String, Set<MatchedInventory>> byProductCode;

    static InventoryIndex of(Collection<MatchedInventory> groups) {
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
        return new InventoryIndex(List.copyOf(groups), byId, byEan, byProductCode);
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

    boolean containsId(String id) {
        return id != null && byId.containsKey(id);
    }

    /**
     * Whether a group of the other (own) index is already represented here. A key carrying a PIM id is covered
     * only by that id, so a store's own product never disappears behind a global group that merely shares a code;
     * a key without one is covered by any EAN or code.
     */
    boolean covers(InventoryKey ownKey) {
        return ownKey.getId() != null ? containsId(ownKey.getId()) : contains(ownKey);
    }

    boolean contains(InventoryKey key) {
        if (key.getId() != null && byId.containsKey(key.getId())) {
            return true;
        }
        return key.getProductEans().stream().anyMatch(byEan::containsKey)
                || key.getProductCodes().stream().anyMatch(byProductCode::containsKey);
    }
}
