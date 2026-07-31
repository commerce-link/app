package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.CategorySelection;
import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InventoryView {

    private final InventoryIndex globalIndex;
    private final InventoryIndex ownIndex;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;
    private final List<InventorySource> sources;

    InventoryView(InventoryIndex globalIndex, InventoryIndex ownIndex,
                  TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry, InventorySource... sources) {
        this.globalIndex = globalIndex;
        this.ownIndex = ownIndex;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
        this.sources = List.of(sources);
    }

    public MatchedInventory findByEan(String ean) {
        return findByInventoryKey(InventoryKey.fromEan(ean));
    }

    public MatchedInventory findByProductCode(String productCode) {
        return findByInventoryKey(InventoryKey.fromMfn(productCode));
    }

    public MatchedInventory findByProduct(Product product) {
        return findByInventoryKey(InventoryKey.fromProduct(product));
    }

    public MatchedInventory findByInventoryKey(InventoryKey lookupKey) {
        return assemble(lookupKey);
    }

    public Collection<MatchedInventory> findAllWithPimId() {
        return listedKeys()
                .filter(key -> key.getId() != null)
                .map(this::assemble)
                .collect(Collectors.toList());
    }

    public Collection<MatchedInventory> findAllByProductCategories(CategorySelection selection) {
        if (selection == null || selection.isEmpty()) {
            return List.of();
        }
        return listedKeys()
                .filter(key -> selection.matches(taxonomyCache.find(key)))
                .map(this::assemble)
                .collect(Collectors.toList());
    }

    public Map<String, Collection<MatchedInventory>> findAllByProductCategoriesGrouped(
            Map<String, CategorySelection> selectionsByKey) {
        if (selectionsByKey == null || selectionsByKey.isEmpty()) {
            return Map.of();
        }
        Map<String, Collection<MatchedInventory>> grouped = new LinkedHashMap<>();
        selectionsByKey.keySet().forEach(key -> grouped.put(key, new LinkedList<>()));
        listedKeys().forEach(inventoryKey -> {
            Taxonomy taxonomy = taxonomyCache.find(inventoryKey);
            List<String> matchedKeys = selectionsByKey.entrySet().stream()
                    .filter(entry -> entry.getValue().matches(taxonomy))
                    .map(Map.Entry::getKey)
                    .toList();
            if (!matchedKeys.isEmpty()) {
                MatchedInventory matched = assemble(inventoryKey);
                matchedKeys.forEach(key -> grouped.get(key).add(matched));
            }
        });
        return grouped;
    }

    private Stream<InventoryKey> listedKeys() {
        return new ListingInventory(globalIndex, ownIndex).keys();
    }

    private MatchedInventory assemble(InventoryKey lookupKey) {
        MatchedInventory result = new MatchedInventory(lookupKey.copy(), taxonomyCache, supplierRegistry);
        for (InventorySource source : sources) {
            source.mergeInto(result, result.getInventoryKey());
        }
        return result;
    }
}
