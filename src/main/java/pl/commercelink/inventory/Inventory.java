package pl.commercelink.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.function.Predicate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class Inventory {

    private final Warehouse warehouse;
    private final StoresRepository storesRepository;
    private final InventoryAutoDiscovery autoDiscovery;
    private final SupplierRegistry supplierRegistry;
    private final StoreInventoryProvider storeInventoryProvider;
    private final GlobalMatchedInventory globalInventory;
    private final PimCatalog pimCatalog;
    private final InventoryMatchingProperties matchingProperties;
    private final ProductDataResolver productDataResolver;

    // Per supplier, the feed version last attempted (successful load or markSeen) — not a guarantee of
    // held inventory; drives the scheduler's "is there a newer feed to load?" check.
    private final ConcurrentHashMap<String, LocalDateTime> lastUpdateDateBySupplier = new ConcurrentHashMap<>();

    // a feed reload and a regroup each read the grouped offers and replace the groups; one at a time, so neither
    // replaces the groups with a result computed from older offers or an older set of EAN-only pairs
    private final Object loadLock = new Object();

    private final Cache<StatisticsKey, InventoryStatistics> statisticsCache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(2))
            .build();

    void init(List<List<InventoryItem>> rawFeeds) {
        synchronized (loadLock) {
            load(
                    rawFeeds.stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList())
            );
        }

        for (List<InventoryItem> rawFeed : rawFeeds) {
            if (!rawFeed.isEmpty()) {
                String supplierName = rawFeed.get(0).supplier();
                lastUpdateDateBySupplier.put(supplierName, LocalDateTime.now());
            }
        }
    }

    void update(Map<String, List<InventoryItem>> rawInventoryItemsBySupplier) {
        System.out.println("Reloading inventory for suppliers: " + rawInventoryItemsBySupplier.keySet());

        synchronized (loadLock) {
            List<InventoryItem> inventoryItemsCopy = globalInventory.allItems();

            inventoryItemsCopy.removeIf(item -> rawInventoryItemsBySupplier.containsKey(item.supplier()));
            for (Map.Entry<String, List<InventoryItem>> entry : rawInventoryItemsBySupplier.entrySet()) {
                inventoryItemsCopy.addAll(entry.getValue());
            }

            load(inventoryItemsCopy);
        }

        rawInventoryItemsBySupplier.keySet().forEach(supplierName -> lastUpdateDateBySupplier.put(supplierName, LocalDateTime.now()));
    }

    /** Groups the offers already in the global pool again, with the EAN-only pairs of the current PIM index. */
    void regroup() {
        synchronized (loadLock) {
            load(globalInventory.allItems());
        }
    }

    private void load(List<InventoryItem> items) {
        PimView pim = pimCatalog.withGlobalData();
        VariantCodes eanOnly = matchingProperties.pimEanOnlyFlag() ? PimEanOnlyCodes.of(pim.findAll()) : VariantCodes.none();
        InventoryAutoDiscovery.Grouping grouping = autoDiscovery.group(items, pim, List::of, VariantCodes.none(), eanOnly);
        globalInventory.replace(grouping.groups(), grouping.variantCodes(), eanOnly);
    }

    public InventoryView withGlobalData() {
        InventoryIndex globalIndex = globalInventory.index();
        return new InventoryView(null, productDataResolver, globalIndex, InventoryIndex.of(List.of()), StoreEntryKeys.none(),
                supplierRegistry, GroupInventorySource.global(globalIndex, supplier -> true));
    }

    public InventoryView withWarehouseDataOnly(String storeId) {
        InventoryIndex globalIndex = InventoryIndex.of(List.of());
        return new InventoryView(storeId, productDataResolver, globalIndex, InventoryIndex.of(List.of()), StoreEntryKeys.none(),
                supplierRegistry, new WarehouseInventorySource(storeId, warehouse.stockQueryService(storeId)));
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
        StoreEntryKeys storeEntries = storeEntryKeys(storeId);
        InventorySource global = GroupInventorySource.global(globalIndex, enabledGlobalSupplier(store), storeEntries);
        InventorySource own = GroupInventorySource.own(ownIndex);
        return view(storeId, storeEntries, globalIndex, ownIndex, global, own, additionalSources);
    }

    private InventoryView scopedView(String storeId, SupplierScope scope, InventorySource... additionalSources) {
        Store store = storesRepository.findById(storeId);
        InventoryIndex ownIndex = storeInventoryProvider.ownIndex(store);
        InventoryIndex globalIndex = globalInventory.index();
        StoreEntryKeys storeEntries = storeEntryKeys(storeId);
        InventorySource global = GroupInventorySource.global(globalIndex, scopedSupplier(store, ConnectionMode.GLOBAL, scope), storeEntries);
        InventorySource own = GroupInventorySource.own(ownIndex, scopedOwnAndManualSupplier(store, scope));
        return view(storeId, storeEntries, globalIndex, ownIndex, global, own, additionalSources);
    }

    private InventoryView view(String storeId, StoreEntryKeys storeEntries, InventoryIndex globalIndex, InventoryIndex ownIndex,
                               InventorySource global, InventorySource own, InventorySource... additionalSources) {
        InventorySource[] sources = Stream.concat(Stream.of(global, own), Arrays.stream(additionalSources))
                .toArray(InventorySource[]::new);
        return new InventoryView(storeId, productDataResolver, globalIndex, ownIndex, storeEntries, supplierRegistry,
                sources);
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

    public InventoryStatistics storeStatistics(String storeId) {
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            return InventoryStatistics.EMPTY;
        }
        Set<String> enabledGlobal = store.getGlobalSupplierNames().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        List<String> ownFingerprint = ownConnectionFingerprint(store);
        StatisticsKey key = new StatisticsKey(storeId, globalInventory.version(), enabledGlobal, ownFingerprint);
        // ownInventory() decodes the whole Redis-cached own feed, so it must run only on a cache miss
        return statisticsCache.get(key, k -> {
            StoreInventory own = storeInventoryProvider.ownInventory(store);
            return InventoryStatisticsCalculator.calculate(globalInventory.index(), enabledGlobal::contains, own.index(), storeEntryKeys(storeId));
        });
    }

    // The store's clones are read once, when the view is built: the lookups run inside loops over every row.
    private StoreEntryKeys storeEntryKeys(String storeId) {
        return new StoreClones(pimCatalog, clonesWithSources(storeId));
    }

    /**
     * The global catalog holds global entries only, so a PIM id it does not know belongs to a store entry (spec 4.3).
     * A clone names the global entry it was cloned from.
     */
    private record StoreClones(PimCatalog pimCatalog, Map<String, String> sourceByClone) implements StoreEntryKeys {

        @Override
        public boolean isStoreEntry(String pimId) {
            return pimCatalog.withGlobalData().findByPimId(pimId).isEmpty();
        }

        @Override
        public String sourceOf(String pimId) {
            return pimId == null ? null : sourceByClone.get(pimId);
        }
    }

    private Map<String, String> clonesWithSources(String storeId) {
        if (storeId == null || storeId.isBlank()) {
            return Map.of();
        }
        Map<String, String> sources = new HashMap<>();
        for (PimEntry clone : pimCatalog.withStoreData(storeId).findOwnClones()) {
            String source = clone.clonedFromPimId();
            if (storeId.equals(clone.ownerStoreId()) && source != null && !source.isBlank()) {
                sources.put(clone.pimId(), source);
            }
        }
        return Map.copyOf(sources);
    }

    static List<String> ownConnectionFingerprint(Store store) {
        return store.getOwnAndManualConnections().stream()
                .map(connection -> connection.getSupplierName() + ":" + connection.getMode() + ":" + connection.isEnabled())
                .sorted()
                .toList();
    }

    public int size() {
        return globalInventory.size();
    }

    public LocalDateTime getLastUpdateDate(String supplierName) {
        return lastUpdateDateBySupplier.getOrDefault(supplierName, LocalDateTime.now().minusDays(365));
    }

    // Mark a feed version attempted without applying it, so a skipped feed isn't re-parsed every cycle.
    public void markSeen(String supplierName, LocalDateTime feedLastModified) {
        lastUpdateDateBySupplier.put(supplierName, feedLastModified);
    }

    public Collection<String> getMatchedSuppliers() {
        return lastUpdateDateBySupplier.keySet();
    }

    private record StatisticsKey(String storeId, long globalVersion, Set<String> enabledGlobalSuppliers, List<String> ownConnectionFingerprint) {
    }
}
