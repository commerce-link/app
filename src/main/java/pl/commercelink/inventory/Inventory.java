package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.function.Predicate;
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
        InventoryIndex globalIndex = globalInventory.index();
        return new InventoryView(globalIndex, InventoryIndex.of(List.of()), taxonomyCache, supplierRegistry,
                GroupInventorySource.global(globalIndex, supplier -> true));
    }

    public InventoryView withWarehouseDataOnly(String storeId) {
        InventoryIndex globalIndex = InventoryIndex.of(List.of());
        return new InventoryView(globalIndex, InventoryIndex.of(List.of()), taxonomyCache, supplierRegistry,
                new WarehouseInventorySource(storeId, warehouse.stockQueryService(storeId)));
    }

    public InventoryView withEnabledSuppliersOnly(String storeId) {
        Store store = storesRepository.findById(storeId);
        InventoryIndex ownIndex = storeInventoryProvider.ownIndex(store);
        InventoryIndex globalIndex = globalInventory.index();
        return new InventoryView(globalIndex, ownIndex, taxonomyCache, supplierRegistry,
                GroupInventorySource.global(globalIndex, enabledGlobalSupplier(store)),
                GroupInventorySource.own(ownIndex));
    }

    public InventoryView withEnabledSuppliersAndWarehouseData(String storeId) {
        Store store = storesRepository.findById(storeId);
        InventoryIndex ownIndex = storeInventoryProvider.ownIndex(store);
        InventoryIndex globalIndex = globalInventory.index();
        return new InventoryView(globalIndex, ownIndex, taxonomyCache, supplierRegistry,
                GroupInventorySource.global(globalIndex, enabledGlobalSupplier(store)),
                GroupInventorySource.own(ownIndex),
                new WarehouseInventorySource(storeId, warehouse.stockQueryService(storeId)));
    }

    public InventoryView withEnabledSuppliersOnly(String storeId, SupplierScope scope) {
        return scopedView(storeId, scope, false);
    }

    public InventoryView withEnabledSuppliersAndWarehouseData(String storeId, SupplierScope scope) {
        return scopedView(storeId, scope, true);
    }

    private InventoryView scopedView(String storeId, SupplierScope scope, boolean withWarehouse) {
        Store store = storesRepository.findById(storeId);
        InventoryIndex ownIndex = storeInventoryProvider.ownIndex(store);
        InventoryIndex globalIndex = globalInventory.index();
        GroupInventorySource global = GroupInventorySource.global(globalIndex, scopedSupplier(store, ConnectionMode.GLOBAL, scope));
        GroupInventorySource own = GroupInventorySource.own(ownIndex, scopedSupplier(store, ConnectionMode.OWN, scope));
        return withWarehouse
                ? new InventoryView(globalIndex, ownIndex, taxonomyCache, supplierRegistry, global, own,
                        new WarehouseInventorySource(storeId, warehouse.stockQueryService(storeId)))
                : new InventoryView(globalIndex, ownIndex, taxonomyCache, supplierRegistry, global, own);
    }

    private Predicate<String> scopedSupplier(Store store, ConnectionMode mode, SupplierScope scope) {
        Collection<String> names = store != null ? store.supplierNames(mode, scope) : List.of();
        return names::contains;
    }

    private Predicate<String> enabledGlobalSupplier(Store store) {
        Collection<String> names = store != null ? store.getGlobalSupplierNames() : List.of();
        return names::contains;
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
