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

    static InventoryStatistics calculate(InventoryIndex globalIndex, Predicate<String> enabledGlobalSupplier, InventoryIndex ownIndex) {
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
                    if (claimedOwnGroups.add(own)) {
                        items.addAll(own.getInventoryItems());
                    }
                }
            }
            tally.add(items);
        }
        for (MatchedInventory own : ownIndex.all()) {
            if (!globalIndex.contains(own.getInventoryKey())) {
                tally.add(own.getInventoryItems());
            }
        }
        return tally.toStatistics();
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
