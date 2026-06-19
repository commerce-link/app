package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class CompositeMatchedInventorySource implements MatchedInventorySource {

    private final Collection<MatchedInventory> own;
    private final GlobalMatchedInventory global;
    private final Set<String> allowedGlobalSuppliers;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    CompositeMatchedInventorySource(Collection<MatchedInventory> own, GlobalMatchedInventory global,
                                    Set<String> allowedGlobalSuppliers, TaxonomyCache taxonomyCache,
                                    SupplierRegistry supplierRegistry) {
        this.own = own;
        this.global = global;
        this.allowedGlobalSuppliers = allowedGlobalSuppliers;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public Collection<MatchedInventory> candidatesFor(InventoryKey key) {
        List<MatchedInventory> ownMatches = ownGroupsMatching(key);
        List<MatchedInventory> globalMatches = narrowToAllowedSuppliers(global.candidatesFor(key));

        List<MatchedInventory> candidates = new ArrayList<>(ownMatches);
        candidates.addAll(globalMatches);
        return mergeGroupsDescribingSameProduct(candidates, identitySetOf(ownMatches));
    }

    @Override
    public Collection<MatchedInventory> all() {
        return mergeOwnGroupsWithMatchingGlobal(own, narrowToAllowedSuppliers(global.all()));
    }

    private List<MatchedInventory> ownGroupsMatching(InventoryKey key) {
        return own.stream()
                .filter(group -> group.matches(key))
                .collect(Collectors.toList());
    }

    private List<MatchedInventory> narrowToAllowedSuppliers(Collection<MatchedInventory> globalGroups) {
        List<MatchedInventory> narrowed = new ArrayList<>();
        for (MatchedInventory group : globalGroups) {
            List<InventoryItem> allowedItems = group.getInventoryItems().stream()
                    .filter(item -> allowedGlobalSuppliers.contains(item.supplier()))
                    .collect(Collectors.toList());
            if (!allowedItems.isEmpty()) {
                narrowed.add(new MatchedInventory(group.getInventoryKey().copy(), allowedItems, taxonomyCache, supplierRegistry));
            }
        }
        return narrowed;
    }

    private List<MatchedInventory> mergeGroupsDescribingSameProduct(List<MatchedInventory> groups, Set<MatchedInventory> ownGroups) {
        SameProductGrouping grouping = new SameProductGrouping();
        groups.forEach(grouping::add);
        for (int i = 0; i < groups.size(); i++) {
            for (int j = i + 1; j < groups.size(); j++) {
                if (groups.get(i).matches(groups.get(j).getInventoryKey())) {
                    grouping.merge(groups.get(i), groups.get(j));
                }
            }
        }
        return mergeEachComponent(grouping.components(), ownGroups);
    }

    private List<MatchedInventory> mergeOwnGroupsWithMatchingGlobal(Collection<MatchedInventory> ownGroups, List<MatchedInventory> globalGroups) {
        Map<String, MatchedInventory> globalByEan = new HashMap<>();
        Map<String, MatchedInventory> globalByMfn = new HashMap<>();
        for (MatchedInventory group : globalGroups) {
            group.getEans().forEach(ean -> globalByEan.put(ean, group));
            group.getMfnCodes().forEach(mfn -> globalByMfn.put(mfn, group));
        }

        SameProductGrouping grouping = new SameProductGrouping();
        Set<MatchedInventory> globalGroupsMerged = identitySetOf(List.of());
        for (MatchedInventory ownGroup : ownGroups) {
            grouping.add(ownGroup);
            for (MatchedInventory globalGroup : globalGroupsMatching(ownGroup, globalByEan, globalByMfn)) {
                grouping.merge(ownGroup, globalGroup);
                globalGroupsMerged.add(globalGroup);
            }
        }

        List<MatchedInventory> result = mergeEachComponent(grouping.components(), identitySetOf(ownGroups));
        for (MatchedInventory globalGroup : globalGroups) {
            if (!globalGroupsMerged.contains(globalGroup)) {
                result.add(globalGroup);
            }
        }
        return result;
    }

    private Set<MatchedInventory> globalGroupsMatching(MatchedInventory ownGroup,
                                                       Map<String, MatchedInventory> globalByEan,
                                                       Map<String, MatchedInventory> globalByMfn) {
        Set<MatchedInventory> matches = new LinkedHashSet<>();
        ownGroup.getEans().forEach(ean -> addIfPresent(matches, globalByEan.get(ean)));
        ownGroup.getMfnCodes().forEach(mfn -> addIfPresent(matches, globalByMfn.get(mfn)));
        return matches;
    }

    private void addIfPresent(Set<MatchedInventory> matches, MatchedInventory group) {
        if (group != null) {
            matches.add(group);
        }
    }

    private List<MatchedInventory> mergeEachComponent(Collection<List<MatchedInventory>> components, Set<MatchedInventory> ownGroups) {
        List<MatchedInventory> result = new ArrayList<>();
        for (List<MatchedInventory> sameProduct : components) {
            if (sameProduct.size() == 1) {
                result.add(sameProduct.get(0));
            } else {
                result.add(mergeIntoSingleGroup(sameProduct, ownGroups));
            }
        }
        return result;
    }

    private MatchedInventory mergeIntoSingleGroup(List<MatchedInventory> groups, Set<MatchedInventory> ownGroups) {
        InventoryKey mergedKey = groups.stream()
                .filter(ownGroups::contains)
                .findFirst()
                .orElse(groups.get(0))
                .getInventoryKey()
                .copy();
        Set<InventoryItem> items = new LinkedHashSet<>();
        groups.forEach(group -> items.addAll(group.getInventoryItems()));
        return new MatchedInventory(mergedKey, items, taxonomyCache, supplierRegistry);
    }

    private static Set<MatchedInventory> identitySetOf(Collection<MatchedInventory> groups) {
        Set<MatchedInventory> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(groups);
        return set;
    }

    private static final class SameProductGrouping {

        private final Map<MatchedInventory, MatchedInventory> parent = new IdentityHashMap<>();

        void add(MatchedInventory group) {
            parent.putIfAbsent(group, group);
        }

        void merge(MatchedInventory first, MatchedInventory second) {
            add(first);
            add(second);
            parent.put(rootOf(first), rootOf(second));
        }

        Collection<List<MatchedInventory>> components() {
            Map<MatchedInventory, List<MatchedInventory>> byRoot = new IdentityHashMap<>();
            for (MatchedInventory group : parent.keySet()) {
                byRoot.computeIfAbsent(rootOf(group), root -> new ArrayList<>()).add(group);
            }
            return byRoot.values();
        }

        private MatchedInventory rootOf(MatchedInventory group) {
            MatchedInventory current = group;
            while (parent.get(current) != current) {
                current = parent.get(current);
            }
            return current;
        }
    }
}
