package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.api.InventoryItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class InventoryStatisticsCalculator {

    private InventoryStatisticsCalculator() {
    }

    static InventoryStatistics calculate(InventoryIndex globalIndex, Predicate<String> enabledGlobalSupplier, InventoryIndex ownIndex,
                                         Predicate<String> isStoreEntry) {
        Tally tally = new Tally();
        boolean hasOwn = !ownIndex.all().isEmpty();
        // an own group's key unions the eans/mfns of all its items, so it can match several global groups;
        // it must join only the first one it matches, or it is counted into bySupplier more than once
        Set<MatchedInventory> claimedOwnGroups = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MatchedInventory group : globalIndex.all()) {
            List<InventoryItem> items = new ArrayList<>();
            for (InventoryItem item : group.getInventoryItems()) {
                if (enabledGlobalSupplier.test(item.supplier())) {
                    items.add(item);
                }
            }
            if (hasOwn) {
                for (MatchedInventory own : ownIndex.findMatching(group.getInventoryKey())) {
                    if (sharesPimEntry(group, own, isStoreEntry) && claimedOwnGroups.add(own)) {
                        items.addAll(own.getInventoryItems());
                    }
                }
            }
            tally.add(items);
        }
        for (MatchedInventory own : ownIndex.all()) {
            if (!globalIndex.covers(own.getInventoryKey(), isStoreEntry)) {
                tally.add(own.getInventoryItems());
            }
        }
        return tally.toStatistics();
    }

    // An own group of a store entry joins only a global group of that same entry or one without any entry (see
    // InventoryIndex.covers); any other own group joins the global group it matches by id, EAN or code.
    private static boolean sharesPimEntry(MatchedInventory global, MatchedInventory own, Predicate<String> isStoreEntry) {
        InventoryKey ownKey = own.getInventoryKey();
        return !InventoryIndex.isStoreEntryKey(ownKey, isStoreEntry)
                || InventoryIndex.sameOrNoEntry(global.getInventoryKey(), ownKey.getId());
    }

    private static final class Tally {
        private int distinctProducts;
        private int productsInStock;
        private final Map<String, int[]> bySupplier = new HashMap<>();

        void add(List<InventoryItem> items) {
            if (items.isEmpty()) {
                return;
            }
            distinctProducts++;
            Set<String> suppliers = new HashSet<>();
            Set<String> suppliersWithStock = new HashSet<>();
            for (InventoryItem item : items) {
                suppliers.add(item.supplier());
                if (item.qty() > 0) {
                    suppliersWithStock.add(item.supplier());
                }
            }
            if (!suppliersWithStock.isEmpty()) {
                productsInStock++;
            }
            suppliers.forEach(supplier -> bySupplier.computeIfAbsent(supplier, s -> new int[2])[0]++);
            suppliersWithStock.forEach(supplier -> bySupplier.get(supplier)[1]++);
        }

        InventoryStatistics toStatistics() {
            if (distinctProducts == 0) {
                return InventoryStatistics.EMPTY;
            }
            Map<String, SupplierOfferStats> stats = new HashMap<>();
            bySupplier.forEach((supplier, counts) -> stats.put(supplier, new SupplierOfferStats(counts[0], counts[1])));
            return new InventoryStatistics(distinctProducts, productsInStock, Map.copyOf(stats));
        }
    }
}
