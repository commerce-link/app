package pl.commercelink.inventory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
class InventoryAutoDiscovery {

    private PimCatalog pimCatalog;
    private SupplierRegistry supplierRegistry;
    private final MatchingRules rules;

    public InventoryAutoDiscovery(PimCatalog pimCatalog, SupplierRegistry supplierRegistry) {
        this(pimCatalog, supplierRegistry, InventoryMatchingProperties.allEnabled());
    }

    @Autowired
    public InventoryAutoDiscovery(PimCatalog pimCatalog, SupplierRegistry supplierRegistry,
                                  InventoryMatchingProperties matchingProperties) {
        this.pimCatalog = pimCatalog;
        this.supplierRegistry = supplierRegistry;
        this.rules = MatchingRules.of(matchingProperties);
    }

    /** Matches against the global PIM view: the shared inventory belongs to no store. */
    Collection<MatchedInventory> run(List<InventoryItem> inventoryItems) {
        return run(inventoryItems, pimCatalog.withGlobalData());
    }

    /** @param pim the PIM view to match known entries in: a store's own inventory uses that store's view */
    Collection<MatchedInventory> run(List<InventoryItem> inventoryItems, PimView pim) {
        return run(inventoryItems, pim, List::of);
    }

    /**
     * @param witnesses offers of another scope that only confirm which keys belong together; they are never added to a group.
     *                  Fetched only when the conflicting-offer guard runs, since the global inventory is large.
     */
    Collection<MatchedInventory> run(List<InventoryItem> allOffers, PimView pim, Supplier<? extends Collection<InventoryItem>> witnesses) {
        return group(allOffers, pim, witnesses, VariantCodes.none()).groups();
    }

    /**
     * The groups of one pool together with its variant codes: the pairs some supplier of the pool (or of an inherited
     * wider pool) lists under several EANs. The codes do not shape the groups; they are returned for linking own
     * groups to global ones.
     */
    record Grouping(Collection<MatchedInventory> groups, VariantCodes variantCodes) {
    }

    /**
     * @param inherited variant codes of a wider pool (a store inherits the global ones); they do not influence the
     *                  groups, only the returned variant codes
     * @return the groups and the variant codes of this pool united with the inherited ones, used to link own groups
     *         to global ones
     */
    Grouping group(List<InventoryItem> allOffers, PimView pim,
                   Supplier<? extends Collection<InventoryItem>> witnesses, VariantCodes inherited) {
        return group(allOffers, pim, witnesses, inherited, VariantCodes.none());
    }

    /**
     * @param eanOnly pairs of the PIM entries an operator flagged to join by EAN only: an offer under such a pair
     *                neither pulls nor is pulled by its code, here or in the PIM step; it joins by EAN alone. The
     *                pairs are also returned among the variant codes, so no own group is linked to a global one by them.
     */
    Grouping group(List<InventoryItem> allOffers, PimView pim,
                   Supplier<? extends Collection<InventoryItem>> witnesses, VariantCodes inherited, VariantCodes eanOnly) {
        SharedManufacturerCodes sharedCodes = SharedManufacturerCodes.of(allOffers);
        CodeJoinGuard guard = rules.codeJoinGuard(sharedCodes, inherited, eanOnly);
        VariantCodes variantCodes = guard.variantCodes();

        OfferPool pool = new OfferPool(allOffers, witnesses, sharedCodes);
        if (!allOffers.isEmpty()) {
            for (OfferFilter filter : rules.offerFilters()) {
                pool = pool.without(filter.reject(pool));
            }
        }
        List<InventoryItem> inventoryItems = pool.offers();

        // Build a mapping of EANs and product codes to inventory items for quick lookup
        Map<String, List<InventoryItem>> mappedByEan = new HashMap<>();
        Map<String, List<InventoryItem>> mappedByProductCode = new HashMap<>();
        for (InventoryItem inventoryItem : inventoryItems) {
            mappedByEan.computeIfAbsent(inventoryItem.ean(), k -> new LinkedList<>()).add(inventoryItem);
            if (!guard.joinsOnlyByEan(inventoryItem)) {
                mappedByProductCode.computeIfAbsent(inventoryItem.mfn(), k -> new LinkedList<>()).add(inventoryItem);
            }
        }

        Map<String, MatchedInventory> keyToMatchedInventory = joinKnownEntries(pim, mappedByEan, mappedByProductCode);
        joinRemainingOffers(mappedByEan, mappedByProductCode, guard, keyToMatchedInventory);

        return new Grouping(processMatchedInventoryCandidates(keyToMatchedInventory.values()), variantCodes);
    }

    /*
        joinKnownEntries and joinRemainingOffers use maps and various other tricks to improve performance of the auto-discovery process. Do not modify unless you are confident in your changes.
     */
    private Map<String, MatchedInventory> joinKnownEntries(PimView pim, Map<String, List<InventoryItem>> mappedByEan,
                                                           Map<String, List<InventoryItem>> mappedByProductCode) {
        // Resolve already known mappings based on the PIM index
        Map<String, MatchedInventory> keyToMatchedInventory = new HashMap<>();
        for (PimEntry pimEntry : pim.findAll()) {
            InventoryKey inventoryKey = InventoryKey.fromPimEntry(pimEntry);

            Collection<InventoryItem> matchingItems = new LinkedHashSet<>();
            matchingItems.addAll(findItemsByKeysModifyingCollection(inventoryKey.getProductEans(), mappedByEan));
            matchingItems.addAll(takeItemsOfBrandModifyingCollection(inventoryKey.getProductCodes(), mappedByProductCode, pimEntry.brand()));

            MatchedInventory matchedInventory = new MatchedInventory(inventoryKey, matchingItems, supplierRegistry);

            for (String ean : matchedInventory.getEans()) {
                keyToMatchedInventory.put(ean, matchedInventory);
            }
        }
        return keyToMatchedInventory;
    }

    private void joinRemainingOffers(Map<String, List<InventoryItem>> mappedByEan, Map<String, List<InventoryItem>> mappedByProductCode,
                                     CodeJoinGuard guard, Map<String, MatchedInventory> keyToMatchedInventory) {
        // Resolve remaining and unknown items through auto-discovery
        for (List<InventoryItem> items : mappedByEan.values()) {
            Set<InventoryItem> matchingItems = new HashSet<>();

            for (InventoryItem itemByEan : items) {
                matchingItems.add(itemByEan);
                if (guard.joinsOnlyByEan(itemByEan)) {
                    continue;
                }
                mappedByProductCode.getOrDefault(itemByEan.mfn(), Collections.emptyList()).stream()
                        .filter(itemByMfn -> itemByMfn.isOfBrand(itemByEan.brand()))
                        .forEach(matchingItems::add);
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
                MatchedInventory mi = new MatchedInventory(ik, matchingItems, supplierRegistry);

                for (String ean : eans) {
                    keyToMatchedInventory.put(ean, mi);
                }
            }
        }
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

    private static Collection<InventoryItem> takeItemsOfBrandModifyingCollection(Collection<String> codes, Map<String, List<InventoryItem>> col, String brand) {
        List<InventoryItem> taken = new ArrayList<>();
        for (String code : codes) {
            List<InventoryItem> items = col.get(code);
            if (items == null) {
                continue;
            }
            List<InventoryItem> ofBrand = items.stream().filter(item -> item.isOfBrand(brand)).toList();
            items.removeAll(ofBrand);
            if (items.isEmpty()) {
                col.remove(code);
            }
            taken.addAll(ofBrand);
        }
        return taken;
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

        for (GroupRefiner refiner : rules.groupRefiners()) {
            Optional<List<List<InventoryItem>>> refined = refiner.refine(inventoryItems);
            if (refined.isPresent()) {
                return refined.get().stream().map(this::createMatchedInventoryOf).toList();
            }
        }

        return Collections.singletonList(matchedInventory);
    }

    // The group is keyed by the EANs and codes of its own offers, so associations from the PIM index are lost.
    // That is intended: these are rare cases of questionable data quality.
    private MatchedInventory createMatchedInventoryOf(List<InventoryItem> inventoryItems) {
        Set<String> eans = inventoryItems.stream().map(InventoryItem::ean).collect(Collectors.toSet());
        Set<String> productCodes = inventoryItems.stream().map(InventoryItem::mfn).collect(Collectors.toSet());
        return new MatchedInventory(new InventoryKey(eans, productCodes), inventoryItems, supplierRegistry);
    }

}
