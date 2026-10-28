package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    /**
     * Whether a group of the other (own) index is already represented here. A key resolved to a store entry (a PIM
     * entry owned by the store, never present in the global catalog) is covered by a group with that id, or by a
     * group without any id that shares its EAN or code: a global supplier selling a product the global PIM lacks,
     * which picks up the store entry's id when assembled. A group of another PIM entry never covers it, so a store's
     * own product never disappears behind a global entry that merely shares a code. Any other key, including one
     * with a global PIM id, is covered by its id, EAN or code: a global group split by price loses its id and keeps
     * only codes, and the own copy of that product must still be deduplicated against it.
     */
    boolean covers(InventoryKey ownKey, StoreEntryKeys storeEntries) {
        if (!isStoreEntryKey(ownKey, storeEntries)) {
            return contains(ownKey);
        }
        return findMatching(ownKey).stream().anyMatch(group -> sameOrNoEntry(group.getInventoryKey(), ownKey.getId(),
                storeEntries.sourceOf(ownKey.getId())));
    }

    // a group belongs to the given PIM entry when it carries that id or no id at all; a clone also owns the groups of
    // the global entry it was cloned from (sourcePimId, null for a plain entry)
    static boolean sameOrNoEntry(InventoryKey key, String entryPimId, String sourcePimId) {
        return key.getId() == null || key.getId().equals(entryPimId)
                || (sourcePimId != null && key.getId().equals(sourcePimId));
    }

    static boolean isStoreEntryKey(InventoryKey key, StoreEntryKeys storeEntries) {
        return key.getId() != null && storeEntries.isStoreEntry(key.getId());
    }

    boolean contains(InventoryKey key) {
        if (key.getId() != null && byId.containsKey(key.getId())) {
            return true;
        }
        return key.getProductEans().stream().anyMatch(byEan::containsKey)
                || key.getProductCodes().stream().anyMatch(byProductCode::containsKey);
    }
}
