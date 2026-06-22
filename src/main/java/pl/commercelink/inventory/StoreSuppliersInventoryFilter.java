package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;

class StoreSuppliersInventoryFilter implements InventoryFilter {

    private final Collection<MatchedInventory> ownInventory;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    StoreSuppliersInventoryFilter(Collection<MatchedInventory> ownInventory, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.ownInventory = ownInventory;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public MatchedInventory apply(MatchedInventory matched) {
        MatchedInventory result = new MatchedInventory(matched.getInventoryKey().copy(), matched.getInventoryItems(), taxonomyCache, supplierRegistry);
        ownInventory.stream()
                .filter(ownGroup -> ownGroup.matches(matched.getInventoryKey()))
                .flatMap(ownGroup -> ownGroup.getInventoryItems().stream())
                .forEach(result::addAlternativeInventoryItem);
        return result;
    }
}
