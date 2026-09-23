package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCatalog;

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
    private final TaxonomyCatalog taxonomyCatalog;
    private final SupplierRegistry supplierRegistry;
    private final List<InventorySource> sources;

    InventoryView(InventoryIndex globalIndex, InventoryIndex ownIndex,
                  TaxonomyCatalog taxonomyCatalog, SupplierRegistry supplierRegistry, InventorySource... sources) {
        this.globalIndex = globalIndex;
        this.ownIndex = ownIndex;
        this.taxonomyCatalog = taxonomyCatalog;
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
        MatchedInventory matched = assemble(lookupKey);
        if (matched.getTaxonomy() == Taxonomy.EMPTY) {
            matched.adoptTaxonomy(taxonomyCatalog.findBest(matched.getInventoryKey().getProductCodes()));
        }
        return matched;
    }

    public Collection<MatchedInventory> findAllWithPimId() {
        return listedKeys()
                .filter(key -> key.getId() != null)
                .map(this::assemble)
                .collect(Collectors.toList());
    }

    public Map<String, Collection<MatchedInventory>> findAllByProductCategoryIds(Collection<String> categoryIds) {
        Map<String, Collection<MatchedInventory>> matchesByCategoryId = new LinkedHashMap<>();
        categoryIds.forEach(categoryId -> matchesByCategoryId.put(categoryId, new LinkedList<>()));
        listedGroups().forEach(group -> {
            String categoryId = group.getTaxonomy().categoryId();
            Collection<MatchedInventory> matches = categoryId == null ? null : matchesByCategoryId.get(categoryId);
            if (matches != null) {
                matches.add(assemble(group.getInventoryKey()));
            }
        });
        return matchesByCategoryId;
    }

    private Stream<MatchedInventory> listedGroups() {
        return new ListingInventory(globalIndex, ownIndex).groups();
    }

    private Stream<InventoryKey> listedKeys() {
        return new ListingInventory(globalIndex, ownIndex).keys();
    }

    private MatchedInventory assemble(InventoryKey lookupKey) {
        MatchedInventory result = new MatchedInventory(lookupKey.copy(), supplierRegistry);
        for (InventorySource source : sources) {
            source.mergeInto(result, result.getInventoryKey());
        }
        return result;
    }
}
