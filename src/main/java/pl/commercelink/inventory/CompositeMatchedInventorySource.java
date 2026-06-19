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
        List<MatchedInventory> matching = new ArrayList<>();
        own.stream().filter(group -> group.matches(key)).forEach(matching::add);
        narrow(global.candidatesFor(key)).forEach(matching::add);
        return coalesce(matching);
    }

    @Override
    public Collection<MatchedInventory> all() {
        List<MatchedInventory> narrowedGlobal = narrow(global.all());
        Map<String, MatchedInventory> globalByEan = new HashMap<>();
        Map<String, MatchedInventory> globalByMfn = new HashMap<>();
        for (MatchedInventory group : narrowedGlobal) {
            group.getEans().forEach(ean -> globalByEan.put(ean, group));
            group.getMfnCodes().forEach(mfn -> globalByMfn.put(mfn, group));
        }
        List<MatchedInventory> ownList = new ArrayList<>(own);
        List<MatchedInventory> globalList = new ArrayList<>(narrowedGlobal);
        int ownSize = ownList.size();
        int globalSize = globalList.size();
        int[] parent = new int[ownSize + globalSize];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Map<MatchedInventory, Integer> globalNodeIndex = new IdentityHashMap<>();
        for (int i = 0; i < globalSize; i++) {
            globalNodeIndex.put(globalList.get(i), ownSize + i);
        }
        for (int i = 0; i < ownSize; i++) {
            MatchedInventory ownGroup = ownList.get(i);
            int ownIdx = i;
            ownGroup.getEans().forEach(ean -> {
                MatchedInventory match = globalByEan.get(ean);
                if (match != null) {
                    union(parent, ownIdx, globalNodeIndex.get(match));
                }
            });
            ownGroup.getMfnCodes().forEach(mfn -> {
                MatchedInventory match = globalByMfn.get(mfn);
                if (match != null) {
                    union(parent, ownIdx, globalNodeIndex.get(match));
                }
            });
        }
        Map<Integer, List<Integer>> components = new HashMap<>();
        for (int i = 0; i < ownSize + globalSize; i++) {
            components.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        List<MatchedInventory> result = new ArrayList<>();
        Set<MatchedInventory> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Integer> component : components.values()) {
            boolean hasOwn = component.stream().anyMatch(idx -> idx < ownSize);
            if (!hasOwn) {
                continue;
            }
            Set<InventoryItem> items = new LinkedHashSet<>();
            InventoryKey mergedKey = null;
            for (int idx : component) {
                if (idx < ownSize) {
                    MatchedInventory ownGroup = ownList.get(idx);
                    if (mergedKey == null) {
                        mergedKey = ownGroup.getInventoryKey().copy();
                    }
                    items.addAll(ownGroup.getInventoryItems());
                } else {
                    MatchedInventory globalGroup = globalList.get(idx - ownSize);
                    items.addAll(globalGroup.getInventoryItems());
                    consumed.add(globalGroup);
                }
            }
            result.add(new MatchedInventory(mergedKey, items, taxonomyCache, supplierRegistry));
        }
        for (MatchedInventory group : narrowedGlobal) {
            if (!consumed.contains(group)) {
                result.add(group);
            }
        }
        return result;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    private List<MatchedInventory> narrow(Collection<MatchedInventory> globalGroups) {
        List<MatchedInventory> narrowed = new ArrayList<>();
        for (MatchedInventory group : globalGroups) {
            List<InventoryItem> allowed = group.getInventoryItems().stream()
                    .filter(item -> allowedGlobalSuppliers.contains(item.supplier()))
                    .collect(Collectors.toList());
            if (!allowed.isEmpty()) {
                narrowed.add(new MatchedInventory(group.getInventoryKey().copy(), allowed, taxonomyCache, supplierRegistry));
            }
        }
        return narrowed;
    }

    private List<MatchedInventory> coalesce(List<MatchedInventory> groups) {
        List<MatchedInventory> components = new ArrayList<>();
        List<List<MatchedInventory>> buckets = new ArrayList<>();
        for (MatchedInventory group : groups) {
            List<List<MatchedInventory>> matching = new ArrayList<>();
            for (List<MatchedInventory> bucket : buckets) {
                if (bucket.stream().anyMatch(member -> member.matches(group.getInventoryKey()))) {
                    matching.add(bucket);
                }
            }
            if (matching.isEmpty()) {
                List<MatchedInventory> newBucket = new ArrayList<>();
                newBucket.add(group);
                buckets.add(newBucket);
            } else {
                List<MatchedInventory> target = matching.get(0);
                target.add(group);
                for (int i = 1; i < matching.size(); i++) {
                    target.addAll(matching.get(i));
                    buckets.remove(matching.get(i));
                }
            }
        }
        for (List<MatchedInventory> bucket : buckets) {
            if (bucket.size() == 1) {
                components.add(bucket.get(0));
                continue;
            }
            Set<InventoryItem> items = new LinkedHashSet<>();
            InventoryKey mergedKey = bucket.get(0).getInventoryKey().copy();
            for (MatchedInventory member : bucket) {
                items.addAll(member.getInventoryItems());
            }
            components.add(new MatchedInventory(mergedKey, items, taxonomyCache, supplierRegistry));
        }
        return components;
    }
}
