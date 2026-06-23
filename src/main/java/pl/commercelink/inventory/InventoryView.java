package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InventoryView {

    private final GlobalInventoryIndex globalIndex;
    private final Collection<MatchedInventory> ownInventory;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;
    private final List<InventorySource> sources;

    InventoryView(GlobalInventoryIndex globalIndex, Collection<MatchedInventory> ownInventory,
                  TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry, InventorySource...    sources) {
        this.globalIndex = globalIndex;
        this.ownInventory = ownInventory;
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

    public Collection<MatchedInventory> findAllByProductCategory(ProductCategory productCategory) {
        return listedKeys()
                .filter(key -> taxonomyCache.find(key).category() == productCategory)
                .map(this::assemble)
                .collect(Collectors.toList());
    }

    private Stream<InventoryKey> listedKeys() {
        return new ListingInventory(globalIndex, ownInventory).keys();
    }

    private MatchedInventory assemble(InventoryKey lookupKey) {
        MatchedInventory result = new MatchedInventory(lookupKey.copy(), taxonomyCache, supplierRegistry);
        for (InventorySource source : sources) {
            source.mergeInto(result, result.getInventoryKey());
        }
        return result;
    }
}
