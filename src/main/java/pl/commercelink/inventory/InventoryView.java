package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.List;
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

    public Collection<MatchedInventory> findAllByProductCategory(String productCategory) {
        return listedKeys()
                .filter(key -> {
                    String category = taxonomyCache.find(key).category();
                    return category == null ? productCategory == null : category.equals(productCategory);
                })
                .map(this::assemble)
                .collect(Collectors.toList());
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
