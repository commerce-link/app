package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Splits a group of offers sharing one manufacturer code into one group per EAN when the EANs are priced
 * far apart: the code is then reused by different products (variants, bundles, a wrong code at a supplier).
 */
final class SingleCodePriceSplit implements GroupRefiner {

    SingleCodePriceSplit() {
    }

    @Override
    public Optional<List<List<InventoryItem>>> refine(List<InventoryItem> offers) {
        if (hasSingleCode(offers)) {
            List<List<InventoryItem>> parts = byEan(offers);
            if (parts.size() > 1) {
                return Optional.of(parts);
            }
        }
        return Optional.empty();
    }

    private static boolean hasSingleCode(List<InventoryItem> inventoryItems) {
        return inventoryItems.stream().map(InventoryItem::mfn).distinct().count() == 1;
    }

    /** @param items offers of one manufacturer code; returned as the only list when there is nothing to split */
    static List<List<InventoryItem>> byEan(List<InventoryItem> items) {
        Map<String, List<InventoryItem>> byEan = new LinkedHashMap<>();
        for (InventoryItem item : items) {
            byEan.computeIfAbsent(item.ean(), k -> new ArrayList<>()).add(item);
        }
        if (byEan.size() < 2) {
            return List.of(items);
        }

        double highest = 0.0;
        double lowest = Double.MAX_VALUE;
        for (Collection<InventoryItem> group : byEan.values()) {
            double average = group.stream().mapToDouble(InventoryItem::netPrice).average().orElse(0.0);
            highest = Math.max(highest, average);
            lowest = Math.min(lowest, average);
        }

        if (PriceSplitThresholds.isTooLowToSplit(PriceSplitThresholds.splitPriceOf(highest))
                || highest / lowest <= PriceSplitThresholds.MAX_RATIO) {
            return List.of(items);
        }
        return List.copyOf(byEan.values());
    }
}
