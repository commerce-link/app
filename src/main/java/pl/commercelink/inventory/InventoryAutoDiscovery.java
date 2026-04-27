package pl.commercelink.inventory;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.*;
import java.util.stream.Collectors;

@Component
class InventoryAutoDiscovery {

    private PimCatalog pimCatalog;
    private TaxonomyCache taxonomyCache;
    private SupplierRegistry supplierRegistry;

    public InventoryAutoDiscovery(PimCatalog pimCatalog, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.pimCatalog = pimCatalog;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    /*
        This method uses maps and various other tricks to improve performance of the auto-discovery process. Do not modify unless you are confident in your changes.
     */
    Collection<MatchedInventory> run(List<InventoryItem> inventoryItems) {
        // Build a mapping of EANs and product codes to inventory items for quick lookup
        Map<String, List<InventoryItem>> mappedByEan = new HashMap<>();
        Map<String, List<InventoryItem>> mappedByProductCode = new HashMap<>();
        for (InventoryItem inventoryItem : inventoryItems) {
            mappedByEan.computeIfAbsent(inventoryItem.ean(), k -> new LinkedList<>()).add(inventoryItem);
            mappedByProductCode.computeIfAbsent(inventoryItem.mfn(), k -> new LinkedList<>()).add(inventoryItem);
        }

        // Resolve already known mappings based on the PIM index
        Map<String, MatchedInventory> keyToMatchedInventory = new HashMap<>();
        for (PimEntry pimEntry : pimCatalog.findAll()) {
            InventoryKey inventoryKey = InventoryKey.fromPimEntry(pimEntry);

            Collection<InventoryItem> matchingItems = new LinkedHashSet<>();
            matchingItems.addAll(findItemsByKeysModifyingCollection(inventoryKey.getProductEans(), mappedByEan));
            matchingItems.addAll(findItemsByKeysModifyingCollection(inventoryKey.getProductCodes(), mappedByProductCode));

            MatchedInventory matchedInventory = new MatchedInventory(inventoryKey, matchingItems, taxonomyCache, supplierRegistry);

            for (String ean : matchedInventory.getEans()) {
                keyToMatchedInventory.put(ean, matchedInventory);
            }
        }

        // Resolve remaining and unknown items through auto-discovery
        for (List<InventoryItem> items : mappedByEan.values()) {
            Set<InventoryItem> matchingItems = new HashSet<>();

            for (InventoryItem itemByEan : items) {
                matchingItems.add(itemByEan);
                matchingItems.addAll(mappedByProductCode.getOrDefault(itemByEan.mfn(), Collections.emptyList()));
            }

            Set<String> eans = matchingItems.stream().map(InventoryItem::ean).collect(Collectors.toSet());
            Set<String> productCodes = matchingItems.stream().map(InventoryItem::mfn).collect(Collectors.toSet());

            MatchedInventory inv = null;
            for (String ean : eans) {
                if (keyToMatchedInventory.containsKey(ean)) {
                    inv = keyToMatchedInventory.get(ean);
                    break;
                }
            }

            if (inv != null) {
                inv.addAlternativeInventoryItems(matchingItems);
            } else {
                InventoryKey ik = new InventoryKey(eans, productCodes);
                MatchedInventory mi = new MatchedInventory(ik, matchingItems, taxonomyCache, supplierRegistry);

                for (String ean : eans) {
                    keyToMatchedInventory.put(ean, mi);
                }
            }
        }

        return processMatchedInventoryCandidates(keyToMatchedInventory.values());
    }

    private static Collection<InventoryItem> findItemsByKeysModifyingCollection(Collection<String> keys, Map<String, List<InventoryItem>> col) {
        return keys
                .stream()
                .map(key -> findItemsByKeyModifyingCollection(key, col))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private static Collection<InventoryItem> findItemsByKeyModifyingCollection(String key, Map<String, List<InventoryItem>> col) {
        List<InventoryItem> l = col.get(key);
        if (l != null) {
            col.remove(key);
            return l;
        }
        return new HashSet<>();
    }

    private Collection<MatchedInventory> processMatchedInventoryCandidates(Collection<MatchedInventory> matchedInventory) {
        return matchedInventory.stream()
                .distinct()
                .map(this::processMatchedInventoryCandidates)
                .flatMap(Collection::stream)
                .filter(i -> !i.isEmpty())
                .collect(Collectors.toSet());
    }

    public List<MatchedInventory> processMatchedInventoryCandidates(MatchedInventory matchedInventory) {

        // nothing to purify as we have offers from just one provider
        if (!matchedInventory.hasOffersFromMultipleSuppliers(2)) {
            return Collections.singletonList(matchedInventory);
        }

        List<InventoryItem> inventoryItems = matchedInventory.getInventoryItems();

        // uniform groups does not require further analysis
        if (isUniformGroupOfItems(inventoryItems)) {
            return Collections.singletonList(matchedInventory);
        }

        var splitPrice = calculateSplitPrice(inventoryItems);

        // price is too small to be considered a valid discriminator
        if (splitPrice < 50) {
            return Collections.singletonList(matchedInventory);
        }

        Map<Boolean, List<InventoryItem>> priceGroupedItems = inventoryItems.stream()
                .collect(Collectors.groupingBy(i -> i.netPrice() > splitPrice));

        List<InventoryItem> lowestPricedGroup = priceGroupedItems.getOrDefault(false, Collections.emptyList());
        List<InventoryItem> highestPricedGroup = priceGroupedItems.getOrDefault(true, Collections.emptyList());

        boolean lowestUsable = lowestPricedGroup.size() > 1;
        boolean highestUsable = highestPricedGroup.size() > 1;

        if (!lowestUsable && !highestUsable) {
            return Collections.singletonList(matchedInventory);
        }

        if (lowestUsable && !highestUsable) {
            return Collections.singletonList(createMatchedInventoryFromPriceGroup(lowestPricedGroup));
        }

        if (!lowestUsable) {
            return Collections.singletonList(createMatchedInventoryFromPriceGroup(highestPricedGroup));
        }

        return processPriceGroups(lowestPricedGroup, highestPricedGroup);
    }

    private List<MatchedInventory> processPriceGroups(List<InventoryItem> lowestPricedGroup, List<InventoryItem> highestPricedGroup) {
        List<MatchedInventory> matchedInventories = new LinkedList<>();

        // analyse price groups
        var lowestPricedGroupMfns = lowestPricedGroup.stream().map(InventoryItem::mfn).distinct().toList();
        if (lowestPricedGroupMfns.size() == 1) {
            String mfn = lowestPricedGroupMfns.getFirst();

            if (doesNotHaveItemsWithMfn(mfn, highestPricedGroup)) {
                // if we have a distinct mfns in price groups we can process them separately
                matchedInventories.add(createMatchedInventoryFromPriceGroup(lowestPricedGroup));
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
                        matchedInventories.add(createMatchedInventoryFromPriceGroup(lowestPricedGroup));
                    }
                }
            }
        }

        matchedInventories.add(createMatchedInventoryFromPriceGroup(highestPricedGroup));
        return matchedInventories;
    }

    // last defence against oddly priced items
    private MatchedInventory createMatchedInventoryFromPriceGroup(List<InventoryItem> inventoryItems) {
        double avg = inventoryItems.stream()
                .mapToDouble(InventoryItem::netPrice)
                .average()
                .getAsDouble();

        List<InventoryItem> approvedItems = inventoryItems.stream().filter(
                i -> i.netPrice() > avg * 0.5 && i.netPrice() < avg * 1.5
        ).toList();

        // as a result, we lose associations from PIM index; however, it's a valid solution because we eliminate rate cases here with questionable data quality
        Set<String> eans = approvedItems.stream().map(InventoryItem::ean).collect(Collectors.toSet());
        Set<String> productCodes = approvedItems.stream().map(InventoryItem::mfn).collect(Collectors.toSet());

        return new MatchedInventory(new InventoryKey(eans, productCodes), approvedItems, taxonomyCache, supplierRegistry);
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
        return mfnToPriceAvg.values().stream().max(Double::compareTo).orElse(0.0) * 0.5;
    }

    private boolean doesNotHaveItemsWithMfn(String mfn, List<InventoryItem> inventoryItems) {
        return inventoryItems.stream().noneMatch(i -> i.mfn().equalsIgnoreCase(mfn));
    }

}
