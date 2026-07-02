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
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return enabledView(storeId);
    }

    public InventoryView withEnabledSuppliersAndWarehouseData(String storeId) {
        return enabledView(storeId, new WarehouseInventorySource(storeId, warehouse.stockQueryService(storeId)));
    }

    public InventoryView withEnabledSuppliersOnly(String storeId, SupplierScope scope) {
        return scopedView(storeId, scope);
    }

    public InventoryView withEnabledSuppliersAndWarehouseData(String storeId, SupplierScope scope) {
        return scopedView(storeId, scope, new WarehouseInventorySource(storeId, warehouse.stockQueryService(storeId)));
    }

    private InventoryView enabledView(String storeId, InventorySource... additionalSources) {
        Store store = storesRepository.findById(storeId);
        InventoryIndex ownIndex = storeInventoryProvider.ownIndex(store);
        InventoryIndex globalIndex = globalInventory.index();
        InventorySource global = GroupInventorySource.global(globalIndex, enabledGlobalSupplier(store));
        InventorySource own = GroupInventorySource.own(ownIndex);
        return view(globalIndex, ownIndex, global, own, additionalSources);
    }

    private InventoryView scopedView(String storeId, SupplierScope scope, InventorySource... additionalSources) {
        Store store = storesRepository.findById(storeId);
        InventoryIndex ownIndex = storeInventoryProvider.ownIndex(store);
        InventoryIndex globalIndex = globalInventory.index();
        InventorySource global = GroupInventorySource.global(globalIndex, scopedSupplier(store, ConnectionMode.GLOBAL, scope));
        InventorySource own = GroupInventorySource.own(ownIndex, scopedOwnAndManualSupplier(store, scope));
        return view(globalIndex, ownIndex, global, own, additionalSources);
    }

    private InventoryView view(InventoryIndex globalIndex, InventoryIndex ownIndex,
                               InventorySource global, InventorySource own, InventorySource... additionalSources) {
        InventorySource[] sources = Stream.concat(Stream.of(global, own), Arrays.stream(additionalSources))
                .toArray(InventorySource[]::new);
        return new InventoryView(globalIndex, ownIndex, taxonomyCache, supplierRegistry, sources);
    }

    private Predicate<String> scopedSupplier(Store store, ConnectionMode mode, SupplierScope scope) {
        Collection<String> names = store != null ? store.supplierNames(mode, scope) : List.of();
        return names::contains;
    }

    private Predicate<String> scopedOwnAndManualSupplier(Store store, SupplierScope scope) {
        Collection<String> names = store != null ? store.ownAndManualSupplierNames(scope) : List.of();
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
