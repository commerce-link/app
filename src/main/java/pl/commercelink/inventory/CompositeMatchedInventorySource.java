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
        List<MatchedInventory> result = new ArrayList<>();
        Set<MatchedInventory> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MatchedInventory ownGroup : own) {
            Set<MatchedInventory> matches = new LinkedHashSet<>();
            ownGroup.getEans().forEach(ean -> {
                MatchedInventory match = globalByEan.get(ean);
                if (match != null) {
                    matches.add(match);
                }
            });
            ownGroup.getMfnCodes().forEach(mfn -> {
                MatchedInventory match = globalByMfn.get(mfn);
                if (match != null) {
                    matches.add(match);
                }
            });
            if (matches.isEmpty()) {
                result.add(ownGroup);
                continue;
            }
            Set<InventoryItem> items = new LinkedHashSet<>(ownGroup.getInventoryItems());
            InventoryKey mergedKey = ownGroup.getInventoryKey().copy();
            for (MatchedInventory match : matches) {
                items.addAll(match.getInventoryItems());
                consumed.add(match);
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
