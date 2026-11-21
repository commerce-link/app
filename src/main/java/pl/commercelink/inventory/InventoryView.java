package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.products.ResolvedProduct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InventoryView {

    // null in the global view: product data then comes from global PIM entries only
    private final String storeId;
    private final ProductDataResolver products;
    private final InventoryIndex globalIndex;
    private final InventoryIndex ownIndex;
    private final StoreEntryKeys storeEntries;
    private final SupplierRegistry supplierRegistry;
    private final List<InventorySource> sources;

    InventoryView(String storeId, ProductDataResolver products, InventoryIndex globalIndex, InventoryIndex ownIndex,
                  StoreEntryKeys storeEntries, SupplierRegistry supplierRegistry, InventorySource... sources) {
        this.storeId = storeId;
        this.products = products;
        this.globalIndex = globalIndex;
        this.ownIndex = ownIndex;
        this.storeEntries = storeEntries;
        this.supplierRegistry = supplierRegistry;
        this.sources = List.of(sources);
    }

    public MatchedInventory findByEan(String ean) {
        return findByInventoryKey(InventoryKey.fromEan(ean));
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

    // a listed global group without a PIM id takes, when assembled, the id of the store entry it covers (see
    // InventoryIndex.covers) or the id of an own group linked with it: that own group is covered by the link and not
    // listed on its own, so dropping the row would drop the product
    private boolean mayCarryPimId(InventoryKey key) {
        return key.getId() != null || ownIndex.findMatching(key).stream()
                .map(MatchedInventory::getInventoryKey)
                .anyMatch(own -> InventoryIndex.isStoreEntryKey(own, storeEntries)
                        || (own.getId() != null && own.isLinkedWith(key)));
    }

    public Map<String, Collection<MatchedInventory>> findAllByProductCategoryIds(Collection<String> categoryIds) {
        Map<String, Collection<MatchedInventory>> matchesByCategoryId = new LinkedHashMap<>();
        categoryIds.forEach(categoryId -> matchesByCategoryId.put(categoryId, new LinkedList<>()));
        List<InventoryKey> listed = listedKeys().toList();
        List<ResolvedProduct> listedProducts = products.ofGroups(storeId, listed);
        List<MatchedInventory> rows = new ArrayList<>();
        for (int i = 0; i < listed.size(); i++) {
            String categoryId = listedProducts.get(i).data().categoryId();
            Collection<MatchedInventory> matches = categoryId == null ? null : matchesByCategoryId.get(categoryId);
            if (matches != null) {
                MatchedInventory row = assemble(listed.get(i));
                matches.add(row);
                rows.add(row);
            }
        }
        // read again for the rows: an assembled row can carry more EANs than its listed key (a linked own group, a
        // store entry), and its product is the one of all of them
        loadProductData(rows);
        return matchesByCategoryId;
    }

    /**
     * Reads the product data of rows found one by one, all in one call. Without it each row reads its own data the
     * first time it is asked, which is right for a single lookup and wasteful in a loop over many.
     */
    public void loadProductData(Collection<MatchedInventory> rows) {
        List<MatchedInventory> missing = rows.stream().filter(row -> !row.isDescribed()).toList();
        if (missing.isEmpty()) {
            return;
        }
        List<ResolvedProduct> found = products.ofGroups(storeId,
                missing.stream().map(MatchedInventory::getInventoryKey).toList());
        for (int i = 0; i < missing.size(); i++) {
            missing.get(i).describeAs(found.get(i));
        }
    }

    private Stream<InventoryKey> listedKeys() {
        return new ListingInventory(globalIndex, ownIndex, storeEntries).keys();
    }

    /**
     * Runs the sources in order, each looking up with the key merged so far. A source may bring in a link (an own group
     * linked to a global group) after earlier sources have already looked, and a later source may have looked only
     * under the EANs known before it: then every source runs once more with the linked key, so a lookup by either EAN
     * of a linked product gives the same row. Rerunning is safe: the result's offers are a set of value-equal records.
     * Without a new link there is exactly one pass.
     */
    private MatchedInventory assemble(InventoryKey lookupKey) {
        MatchedInventory result = new MatchedInventory(lookupKey.copy(), supplierRegistry);
        result.readProductFrom(key -> products.ofGroups(storeId, List.of(key)).get(0));
        Collection<String> linkedBefore = result.getInventoryKey().getLinkedEans();
        runSources(result);
        if (!linkedBefore.containsAll(result.getInventoryKey().getLinkedEans())) {
            runSources(result);
        }
        return result;
    }

    private void runSources(MatchedInventory result) {
        for (InventorySource source : sources) {
            source.mergeInto(result, result.getInventoryKey());
        }
    }
}
