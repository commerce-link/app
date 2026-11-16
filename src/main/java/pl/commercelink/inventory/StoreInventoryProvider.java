package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StoreInventoryProvider {

    // how long a stale snapshot stays readable past its freshness, so a request is served while the rebuild runs
    private static final Duration STALE_RETENTION = Duration.ofHours(24);

    private final StoreInventoryCache cache;
    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final InventoryAutoDiscovery autoDiscovery;
    private final StoreFeedItemLoader storeFeedItemLoader;
    private final ExchangeRates exchangeRates;
    private final PimCatalog pimCatalog;
    private final StoreInventoryRefresher refresher;

    private final Map<String, Object> refreshLocks = new ConcurrentHashMap<>();

    @Value("${inventory.store-cache.ttl-minutes}")
    long defaultTtlMinutes;

    public StoreInventory get(String storeId) {
        Optional<StoreInventory> cached = cache.get(storeId);
        if (cached.isEmpty()) {
            return buildAndCache(storesRepository.findById(storeId), storeId);
        }
        return servingStale(cached.get(), storeId, () -> storesRepository.findById(storeId));
    }

    public InventoryIndex ownIndex(Store store) {
        return ownInventory(store).index();
    }

    public StoreInventory ownInventory(Store store) {
        if (store == null || !store.hasOwnOrManualSupplierConnections()) {
            return new StoreInventory(InventoryIndex.of(List.of()), null);
        }
        String storeId = store.getStoreId();
        Optional<StoreInventory> cached = cache.get(storeId);
        if (cached.isEmpty()) {
            return buildAndCache(store, storeId);
        }
        return servingStale(cached.get(), storeId, () -> store);
    }

    // Refreshes of one store run one after another on this instance, each reading the feed files only once it holds
    // the lock: otherwise a rebuild started before a feed import could finish after it and overwrite the newer snapshot.
    public void refresh(String storeId) {
        synchronized (refreshLocks.computeIfAbsent(storeId, id -> new Object())) {
            buildAndCache(storesRepository.findById(storeId), storeId);
        }
    }

    private StoreInventory servingStale(StoreInventory cached, String storeId, Supplier<Store> store) {
        LocalDateTime builtAt = cached.builtAt();
        if (builtAt != null && builtAt.plus(freshFor(store.get())).isBefore(LocalDateTime.now())) {
            refresher.refreshInBackground(storeId, () -> refresh(storeId));
        }
        return cached;
    }

    private StoreInventory buildAndCache(Store store, String storeId) {
        StoreInventory built = build(storeId, store);
        cache.put(storeId, built, freshFor(store).plus(STALE_RETENTION));
        return built;
    }

    private Duration freshFor(Store storeEntity) {
        long minutes = Optional.ofNullable(storeEntity)
                .flatMap(Store::getInventoryCacheTtlMinutes)
                .filter(value -> value > 0)
                .map(Integer::longValue)
                .orElse(defaultTtlMinutes);
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private StoreInventory build(String storeId, Store storeEntity) {
        if (storeEntity == null) {
            return new StoreInventory(InventoryIndex.of(List.of()), LocalDateTime.now());
        }

        List<InventoryItem> ownItems = new ArrayList<>();
        Map<String, Double> sellRates = exchangeRates.getCurrentSellRates();
        for (StoreSupplierConnection connection : storeEntity.getOwnAndManualConnections()) {
            if (!connection.isEnabled()) {
                continue;
            }
            SupplierProviderDescriptor descriptor = descriptorFor(connection);
            if (descriptor != null) {
                ownItems.addAll(storeFeedItemLoader.load(storeId, connection.getSupplierName(), descriptor, sellRates));
            }
        }

        return new StoreInventory(InventoryIndex.of(autoDiscovery.run(ownItems, pimCatalog.withStoreData(storeId))), LocalDateTime.now());
    }

    private SupplierProviderDescriptor descriptorFor(StoreSupplierConnection connection) {
        String identity = connection.getSupplierName();
        if (connection.getMode() == ConnectionMode.MANUAL) {
            return ManualSupplierDescriptor.forIdentity(identity);
        }
        return supplierProviderFactory.getDescriptor(identity);
    }
}
