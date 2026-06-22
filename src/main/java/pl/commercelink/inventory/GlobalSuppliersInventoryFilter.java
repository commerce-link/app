package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.stream.Collectors;

class GlobalSuppliersInventoryFilter implements InventoryFilter {

    private final Collection<String> globalSupplierNames;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    GlobalSuppliersInventoryFilter(Collection<String> globalSupplierNames, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.globalSupplierNames = globalSupplierNames;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public MatchedInventory apply(MatchedInventory matched) {
        Collection<InventoryItem> globalItems = matched.getInventoryItems().stream()
                .filter(item -> globalSupplierNames.contains(item.supplier()))
                .collect(Collectors.toList());
        return new MatchedInventory(matched.getInventoryKey().copy(), globalItems, taxonomyCache, supplierRegistry);
    }
}
