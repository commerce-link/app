package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InventoryView {

    private final InventoryIndex globalIndex;
    private final InventoryIndex ownIndex;
    private final StoreEntryKeys storeEntries;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;
    private final List<InventorySource> sources;

    InventoryView(InventoryIndex globalIndex, InventoryIndex ownIndex, StoreEntryKeys storeEntries,
                  TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry, InventorySource... sources) {
        this.globalIndex = globalIndex;
        this.ownIndex = ownIndex;
        this.storeEntries = storeEntries;
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
                .filter(this::mayCarryPimId)
                .map(this::assemble)
                .filter(matched -> matched.getInventoryKey().getId() != null)
                .collect(Collectors.toList());
    }

    // a listed global group without a PIM id takes the id of the store entry it covers when assembled
    // (see InventoryIndex.covers)
    private boolean mayCarryPimId(InventoryKey key) {
        return key.getId() != null || ownIndex.findMatching(key).stream()
                .anyMatch(own -> InventoryIndex.isStoreEntryKey(own.getInventoryKey(), storeEntries));
    }

    public Map<String, Collection<MatchedInventory>> findAllByProductCategoryIds(Collection<String> categoryIds) {
        Map<String, Collection<MatchedInventory>> matchesByCategoryId = new LinkedHashMap<>();
        categoryIds.forEach(categoryId -> matchesByCategoryId.put(categoryId, new LinkedList<>()));
        listedKeys().forEach(key -> {
            String categoryId = taxonomyCache.find(key).categoryId();
            Collection<MatchedInventory> matches = categoryId == null ? null : matchesByCategoryId.get(categoryId);
            if (matches != null) {
                matches.add(assemble(key));
            }
        });
        return matchesByCategoryId;
    }

    private Stream<InventoryKey> listedKeys() {
        return new ListingInventory(globalIndex, ownIndex, storeEntries).keys();
    }

    private MatchedInventory assemble(InventoryKey lookupKey) {
        MatchedInventory result = new MatchedInventory(lookupKey.copy(), taxonomyCache, supplierRegistry);
        for (InventorySource source : sources) {
            source.mergeInto(result, result.getInventoryKey());
        }
        return result;
    }
}
