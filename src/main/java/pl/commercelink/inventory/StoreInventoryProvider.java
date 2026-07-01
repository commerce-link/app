package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
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

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StoreInventoryProvider {

    private final StoreInventoryCache cache;
    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final InventoryAutoDiscovery autoDiscovery;
    private final StoreFeedItemLoader storeFeedItemLoader;
    private final ExchangeRates exchangeRates;

    @Value("${inventory.store-cache.ttl-minutes}")
    long defaultTtlMinutes;

    public StoreInventory get(String storeId) {
        return cache.get(storeId).orElseGet(() -> buildAndCache(storesRepository.findById(storeId), storeId));
    }

    public InventoryIndex ownIndex(Store store) {
        if (store == null || !store.hasOwnOrManualSupplierConnections()) {
            return InventoryIndex.of(List.of());
        }
        String storeId = store.getStoreId();
        return cache.get(storeId).orElseGet(() -> buildAndCache(store, storeId)).index();
    }

    private StoreInventory buildAndCache(Store store, String storeId) {
        StoreInventory built = build(storeId, store);
        cache.put(storeId, built, resolveTtl(store));
        return built;
    }

    private Duration resolveTtl(Store storeEntity) {
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
                ownItems.addAll(storeFeedItemLoader.load(storeId, descriptor, sellRates));
            }
        }

        return new StoreInventory(InventoryIndex.of(autoDiscovery.run(ownItems)), LocalDateTime.now());
    }

    private SupplierProviderDescriptor descriptorFor(StoreSupplierConnection connection) {
        if (connection.getMode() == ConnectionMode.MANUAL) {
            return new ManualSupplierDescriptor(ManualSupplierInfos.label(connection.getSupplierName()));
        }
        return supplierProviderFactory.getDescriptor(connection.getSupplierName());
    }
}
