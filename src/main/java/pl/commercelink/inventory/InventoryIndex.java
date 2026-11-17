package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class InventoryIndex {

    private final List<MatchedInventory> groups;
    private final Map<String, Set<MatchedInventory>> byId;
    private final Map<String, Set<MatchedInventory>> byEan;
    // groups linked to a global group (own groups only), by the linked EAN; empty while no group carries a link
    private final Map<String, Set<MatchedInventory>> byLinkedEan;

    static InventoryIndex of(Collection<MatchedInventory> groups) {
        Map<String, Set<MatchedInventory>> byId = new HashMap<>();
        Map<String, Set<MatchedInventory>> byEan = new HashMap<>();
        Map<String, Set<MatchedInventory>> byLinkedEan = new HashMap<>();
        for (MatchedInventory group : groups) {
            InventoryKey key = group.getInventoryKey();
            if (key.getId() != null) {
                byId.computeIfAbsent(key.getId(), k -> new LinkedHashSet<>()).add(group);
            }
            key.getProductEans().forEach(ean -> byEan.computeIfAbsent(ean, k -> new LinkedHashSet<>()).add(group));
            indexLink(byLinkedEan, group);
        }
        return new InventoryIndex(List.copyOf(groups), byId, byEan, byLinkedEan);
    }

    /**
     * Links a group of this index to the global group with the given EANs: the group's key carries the link and the
     * group is found by those EANs from then on. Not thread-safe: link the groups before the index is published.
     */
    void link(MatchedInventory group, Collection<String> globalEans) {
        if (!belongs(group)) {
            throw new IllegalArgumentException("Group is not part of this index: " + group.getInventoryKey());
        }
        InventoryKey key = group.getInventoryKey();
        for (String ean : key.getLinkedEans()) {
            Set<MatchedInventory> linked = byLinkedEan.get(ean);
            if (linked != null && linked.remove(group) && linked.isEmpty()) {
                byLinkedEan.remove(ean);
            }
        }
        key.linkTo(globalEans);
        indexLink(byLinkedEan, group);
    }

    private static void indexLink(Map<String, Set<MatchedInventory>> byLinkedEan, MatchedInventory group) {
        group.getInventoryKey().getLinkedEans()
                .forEach(ean -> byLinkedEan.computeIfAbsent(ean, k -> new LinkedHashSet<>()).add(group));
    }

    private boolean belongs(MatchedInventory group) {
        InventoryKey key = group.getInventoryKey();
        if (key.getId() != null && byId.getOrDefault(key.getId(), Set.of()).contains(group)) {
            return true;
        }
        Collection<String> eans = key.getProductEans();
        if (!eans.isEmpty()) {
            return byEan.getOrDefault(eans.iterator().next(), Set.of()).contains(group);
        }
        // a group without id and EAN is in no map; rare enough for a scan
        return groups.stream().anyMatch(candidate -> candidate == group);
    }

    List<MatchedInventory> all() {
        return groups;
    }

    List<MatchedInventory> findMatching(InventoryKey key) {
        Set<MatchedInventory> matches = new LinkedHashSet<>();
        if (key.getId() != null) {
            matches.addAll(byId.getOrDefault(key.getId(), Set.of()));
        }
        Collection<String> eans = key.getProductEans();
        eans.forEach(ean -> matches.addAll(byEan.getOrDefault(ean, Set.of())));
        // linked groups come last, so a match by id or EAN keeps its place
        if (!byLinkedEan.isEmpty()) {
            eans.forEach(ean -> matches.addAll(byLinkedEan.getOrDefault(ean, Set.of())));
        }
        key.getLinkedEans().forEach(ean -> matches.addAll(byEan.getOrDefault(ean, Set.of())));
        return List.copyOf(matches);
    }

    /**
     * Whether a group of the other (own) index is already represented here. A key resolved to a store entry (a PIM
     * entry owned by the store, never present in the global catalog) is covered by a group with that id, or by a
     * group without any id that shares its EAN: a global supplier selling a product the global PIM lacks, which
     * picks up the store entry's id when assembled. A group of another PIM entry never covers it. Any other key,
     * including one with a global PIM id, is covered by its id or EAN: a global group split by price loses its id
     * and keeps its EANs, and the own copy of that product must still be deduplicated against it. A group linked
     * with the key (see {@link InventoryKey#isLinkedWith}) covers it as well.
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
        Collection<String> eans = key.getProductEans();
        if (eans.stream().anyMatch(byEan::containsKey)) {
            return true;
        }
        return (!byLinkedEan.isEmpty() && eans.stream().anyMatch(byLinkedEan::containsKey))
                || (!key.getLinkedEans().isEmpty() && key.getLinkedEans().stream().anyMatch(byEan::containsKey));
    }
}
