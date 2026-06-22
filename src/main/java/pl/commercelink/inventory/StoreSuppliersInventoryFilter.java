package pl.commercelink.inventory;

import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

class StoreSuppliersInventoryFilter implements InventoryFilter {

    private final Collection<String> globalSupplierNames;
    private final Collection<MatchedInventory> ownInventory;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    StoreSuppliersInventoryFilter(String storeId, StoresRepository storesRepository, StoreInventoryProvider storeInventoryProvider,
                                  TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        Store store = storesRepository.findById(storeId);
        this.globalSupplierNames = store != null ? store.getGlobalSupplierNames() : List.of();
        this.ownInventory = store != null && store.hasOwnSupplierConnections()
                ? storeInventoryProvider.get(storeId).items()
                : new LinkedList<>();
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public MatchedInventory apply(MatchedInventory matched) {
        Collection<InventoryItem> globalItems = matched.getInventoryItems().stream()
                .filter(item -> globalSupplierNames.contains(item.supplier()))
                .collect(Collectors.toList());

        MatchedInventory result = new MatchedInventory(matched.getInventoryKey().copy(), globalItems, taxonomyCache, supplierRegistry);
        ownInventory.stream()
                .filter(ownGroup -> ownGroup.matches(matched.getInventoryKey()))
                .flatMap(ownGroup -> ownGroup.getInventoryItems().stream())
                .forEach(result::addAlternativeInventoryItem);
        return result;
    }
}
