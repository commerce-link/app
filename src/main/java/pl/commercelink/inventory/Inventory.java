package pl.commercelink.inventory;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
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
public class Inventory {

    private final Warehouse warehouse;
    private final StoresRepository storesRepository;
    private final InventoryAutoDiscovery autoDiscovery;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    private Collection<MatchedInventory> autoDiscoveredInventory = new LinkedList<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastUpdateDateBySupplier = new ConcurrentHashMap<>();

    Inventory(Warehouse warehouse, StoresRepository storesRepository, InventoryAutoDiscovery autoDiscovery, TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.warehouse = warehouse;
        this.storesRepository = storesRepository;
        this.autoDiscovery = autoDiscovery;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

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

        List<InventoryItem> inventoryItemsCopy = getInventoryItemsAsShallowCopy();

        inventoryItemsCopy.removeIf(item -> rawInventoryItemsBySupplier.containsKey(item.supplier()));
        for (Map.Entry<String, List<InventoryItem>> entry : rawInventoryItemsBySupplier.entrySet()) {
            inventoryItemsCopy.addAll(entry.getValue());
        }

        load(inventoryItemsCopy);

        // set new last update date for all suppliers that were reloaded
        rawInventoryItemsBySupplier.keySet().forEach(supplierName -> lastUpdateDateBySupplier.put(supplierName, LocalDateTime.now()));
    }

    private void load(List<InventoryItem> items) {
        long timestamp = System.currentTimeMillis();
        Collection<MatchedInventory> autoDiscoveredItems = autoDiscovery.run(items);

        synchronized (this) {
            this.autoDiscoveredInventory = autoDiscoveredItems;

            System.out.println("Auto-discovered took " + (System.currentTimeMillis() - timestamp) + "ms");
            System.out.println("Auto-discovered items: " + size());
            System.out.println("Auto-discovered suppliers: " + getMatchedSuppliers());
        }
    }

    // returns a copy of the inventory items list to be modified without affecting the original inventory
    private List<InventoryItem> getInventoryItemsAsShallowCopy() {
        return autoDiscoveredInventory.stream()
                .flatMap(i -> i.getInventoryItems().stream())
                .collect(Collectors.toList());
    }

    public InventoryView withGlobalData() {
        return new InventoryView(autoDiscoveredInventory);
    }

    public InventoryView withWarehouseDataOnly(String storeId) {
        return new InventoryView(new LinkedList<>(), new WarehouseInventoryFilter(storeId, warehouse.stockQueryService(storeId), taxonomyCache, supplierRegistry));
    }

    public InventoryView withEnabledSuppliersOnly(String storeId) {
        return new InventoryView(autoDiscoveredInventory, new EnabledSuppliersInventoryFilter(storeId, storesRepository, taxonomyCache, supplierRegistry));
    }

    public InventoryView withEnabledSuppliersAndWarehouseData(String storeId) {
        return new InventoryView(
                autoDiscoveredInventory,
                new WarehouseInventoryFilter(storeId, warehouse.stockQueryService(storeId), taxonomyCache, supplierRegistry),
                new EnabledSuppliersInventoryFilter(storeId, storesRepository, taxonomyCache, supplierRegistry)
        );
    }

    public int size() {
        return autoDiscoveredInventory.size();
    }

    public LocalDateTime getLastUpdateDate(String supplierName) {
        return lastUpdateDateBySupplier.getOrDefault(supplierName, LocalDateTime.now().minusDays(365));
    }

    public Collection<String> getMatchedSuppliers() {
        return lastUpdateDateBySupplier.keySet();
    }
}
