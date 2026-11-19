package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Price control of a group mixing several EANs and manufacturer codes: splits it into a cheap and an expensive part
 * when its prices are far apart, keeps a part only when it can tell it apart by code, and drops oddly priced offers.
 * A part may come back empty.
 */
final class MixedCodePriceControl implements GroupRefiner {

    @Override
    public Optional<List<List<InventoryItem>>> refine(List<InventoryItem> inventoryItems) {

        // uniform groups does not require further analysis
        if (isUniformGroupOfItems(inventoryItems)) {
            return Optional.empty();
        }

        var splitPrice = calculateSplitPrice(inventoryItems);

        // price is too small to be considered a valid discriminator
        if (PriceSplitThresholds.isTooLowToSplit(splitPrice)) {
            return Optional.empty();
        }

        Map<Boolean, List<InventoryItem>> priceGroupedItems = inventoryItems.stream()
                .collect(Collectors.groupingBy(i -> i.netPrice() > splitPrice));

        List<InventoryItem> lowestPricedGroup = priceGroupedItems.getOrDefault(false, Collections.emptyList());
        List<InventoryItem> highestPricedGroup = priceGroupedItems.getOrDefault(true, Collections.emptyList());

        boolean lowestUsable = lowestPricedGroup.size() > 1;
        boolean highestUsable = highestPricedGroup.size() > 1;

        if (!lowestUsable && !highestUsable) {
            return Optional.empty();
        }

        if (lowestUsable && !highestUsable) {
            return Optional.of(Collections.singletonList(approvedItemsOfPriceGroup(lowestPricedGroup)));
        }

        if (!lowestUsable) {
            return Optional.of(Collections.singletonList(approvedItemsOfPriceGroup(highestPricedGroup)));
        }

        return Optional.of(processPriceGroups(lowestPricedGroup, highestPricedGroup));
    }

    private List<List<InventoryItem>> processPriceGroups(List<InventoryItem> lowestPricedGroup, List<InventoryItem> highestPricedGroup) {
        List<List<InventoryItem>> matchedInventories = new LinkedList<>();

        // analyse price groups
        var lowestPricedGroupMfns = lowestPricedGroup.stream().map(InventoryItem::mfn).distinct().toList();
        if (lowestPricedGroupMfns.size() == 1) {
            String mfn = lowestPricedGroupMfns.getFirst();

            if (doesNotHaveItemsWithMfn(mfn, highestPricedGroup)) {
                // if we have a distinct mfns in price groups we can process them separately
                matchedInventories.add(approvedItemsOfPriceGroup(lowestPricedGroup));
            } else {

                // otherwise if we have an exact match in the lowest priced group, we can try to rebalance the highest priced group to find a uniform group of items
                var lowestPricedGroupEans = lowestPricedGroup.stream().map(InventoryItem::ean).distinct().toList();
                if (lowestPricedGroupEans.size() == 1) {
                    String ean = lowestPricedGroupEans.getFirst();

                    // rebalance perfectly matched items to see if we can find a uniform group
                    List<InventoryItem> perfectlyMatchedItems = highestPricedGroup.stream()
                            .filter(i -> i.ean().equalsIgnoreCase(ean))
                            .filter(i -> i.mfn().equalsIgnoreCase(mfn))
                            .toList();

                    lowestPricedGroup.addAll(perfectlyMatchedItems);
                    highestPricedGroup.removeAll(perfectlyMatchedItems);

                    if (doesNotHaveItemsWithMfn(mfn, highestPricedGroup)) {
                        matchedInventories.add(approvedItemsOfPriceGroup(lowestPricedGroup));
                    }
                }
            }
        }

        matchedInventories.add(approvedItemsOfPriceGroup(highestPricedGroup));
        return matchedInventories;
    }

    // last defence against oddly priced items
    private List<InventoryItem> approvedItemsOfPriceGroup(List<InventoryItem> inventoryItems) {
        double avg = inventoryItems.stream()
                .mapToDouble(InventoryItem::netPrice)
                .average()
                .getAsDouble();

        List<InventoryItem> approvedItems = inventoryItems.stream().filter(
                i -> i.netPrice() > avg * 0.5 && i.netPrice() < avg * 1.5
        ).toList();

        return approvedItems;
    }

    private boolean isUniformGroupOfItems(List<InventoryItem> inventoryItems) {
        var eanToOccurrencesQty = new HashMap<String, Integer>();
        for (InventoryItem inventoryItem : inventoryItems) {
            eanToOccurrencesQty.compute(inventoryItem.ean(), (k, v) -> v == null ? 1 : v + 1);
        }

        var mfnToOccurrencesQty = new HashMap<String, Integer>();
        for (InventoryItem inventoryItem : inventoryItems) {
            mfnToOccurrencesQty.compute(inventoryItem.mfn(), (k, v) -> v == null ? 1 : v + 1);
        }

        return mfnToOccurrencesQty.size() == 1 || eanToOccurrencesQty.size() == 1;
    }

    // this is to avoid extreme situations when providers misassign ean or mfn keys and we end up with too big price distribution
    private double calculateSplitPrice(List<InventoryItem> inventoryItems) {
        var mfnToPriceAvg = new HashMap<String, Double>();
        for (InventoryItem inventoryItem : inventoryItems) {
            mfnToPriceAvg.compute(inventoryItem.mfn(), (k, v) -> v == null ?
                    inventoryItem.netPrice() : (v + inventoryItem.netPrice()) / 2);
        }
        return PriceSplitThresholds.splitPriceOf(mfnToPriceAvg.values().stream().max(Double::compareTo).orElse(0.0));
    }

    private boolean doesNotHaveItemsWithMfn(String mfn, List<InventoryItem> inventoryItems) {
        return inventoryItems.stream().noneMatch(i -> i.mfn().equalsIgnoreCase(mfn));
    }
}
