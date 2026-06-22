package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Inventory {

    private final Warehouse warehouse;
    private final StoresRepository storesRepository;
    private final InventoryAutoDiscovery autoDiscovery;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;
    private final StoreInventoryProvider storeInventoryProvider;
    private final GlobalMatchedInventory globalInventory;

    private final ConcurrentHashMap<String, LocalDateTime> lastUpdateDateBySupplier = new ConcurrentHashMap<>();

    void init(List<List<InventoryItem>> rawFeeds) {
        load(
                rawFeeds.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
        );

        for (List<InventoryItem> rawFeed : rawFeeds) {
            if (!rawFeed.isEmpty()) {
                String supplierName = rawFeed.get(0).supplier();
                lastUpdateDateBySupplier.put(supplierName, LocalDateTime.now());
            }
        }
    }

    void update(Map<String, List<InventoryItem>> rawInventoryItemsBySupplier) {
        System.out.println("Reloading inventory for suppliers: " + rawInventoryItemsBySupplier.keySet());

        List<InventoryItem> inventoryItemsCopy = globalInventory.allItems();

        inventoryItemsCopy.removeIf(item -> rawInventoryItemsBySupplier.containsKey(item.supplier()));
        for (Map.Entry<String, List<InventoryItem>> entry : rawInventoryItemsBySupplier.entrySet()) {
            inventoryItemsCopy.addAll(entry.getValue());
        }

        load(inventoryItemsCopy);

        rawInventoryItemsBySupplier.keySet().forEach(supplierName -> lastUpdateDateBySupplier.put(supplierName, LocalDateTime.now()));
    }

    private void load(List<InventoryItem> items) {
        Collection<MatchedInventory> autoDiscoveredItems = autoDiscovery.run(items);
        globalInventory.replace(autoDiscoveredItems);
    }

    public InventoryView withGlobalData() {
        return new InventoryView(globalInventory.all());
    }

    public InventoryView withWarehouseDataOnly(String storeId) {
        return new InventoryView(new LinkedList<>(),
                new WarehouseInventoryFilter(storeId, warehouse.stockQueryService(storeId), taxonomyCache, supplierRegistry));
    }

    public InventoryView withEnabledSuppliersOnly(String storeId) {
        Store store = storesRepository.findById(storeId);
        Collection<MatchedInventory> ownInventory = storeInventoryProvider.ownInventory(store);
        return new InventoryView(globalInventory.all(), ownInventory,
                new GlobalSuppliersInventoryFilter(globalSupplierNames(store), taxonomyCache, supplierRegistry),
                new StoreSuppliersInventoryFilter(ownInventory, taxonomyCache, supplierRegistry));
    }

    public InventoryView withEnabledSuppliersAndWarehouseData(String storeId) {
        Store store = storesRepository.findById(storeId);
        Collection<MatchedInventory> ownInventory = storeInventoryProvider.ownInventory(store);
        return new InventoryView(globalInventory.all(), ownInventory,
                new GlobalSuppliersInventoryFilter(globalSupplierNames(store), taxonomyCache, supplierRegistry),
                new StoreSuppliersInventoryFilter(ownInventory, taxonomyCache, supplierRegistry),
                new WarehouseInventoryFilter(storeId, warehouse.stockQueryService(storeId), taxonomyCache, supplierRegistry));
    }

    private Collection<String> globalSupplierNames(Store store) {
        return store != null ? store.getGlobalSupplierNames() : List.of();
    }

    public int size() {
        return globalInventory.size();
    }

    public LocalDateTime getLastUpdateDate(String supplierName) {
        return lastUpdateDateBySupplier.getOrDefault(supplierName, LocalDateTime.now().minusDays(365));
    }

    public Collection<String> getMatchedSuppliers() {
        return lastUpdateDateBySupplier.keySet();
    }
}
