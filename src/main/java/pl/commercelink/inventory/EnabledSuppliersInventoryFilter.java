package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.stream.Collectors;

class EnabledSuppliersInventoryFilter implements InventoryFilter {

    private final Collection<String> enabledSupplierNames;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    EnabledSuppliersInventoryFilter(String storeId, StoresRepository storesRepository, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.enabledSupplierNames = storesRepository.findById(storeId).getEnabledProviders();
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public MatchedInventory apply(MatchedInventory matched) {
        Collection<InventoryItem> enabledItems = matched.getInventoryItems().stream()
                .filter(item -> SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.supplier()) || enabledSupplierNames.contains(item.supplier()))
                .collect(Collectors.toList());
        return new MatchedInventory(matched.getInventoryKey().copy(), enabledItems, taxonomyCache, supplierRegistry);
    }
}
