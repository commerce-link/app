package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
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
    private final GlobalMatchedInventory globalInventory;
    private final SupplierRegistry supplierRegistry;
    private final InventoryAutoDiscovery autoDiscovery;
    private final StoreFeedItemLoader storeFeedItemLoader;
    private final ExchangeRates exchangeRates;

    @Value("${inventory.store-cache.ttl-minutes:60}")
    private long defaultTtlMinutes;

    public StoreInventory get(String storeId) {
        Optional<StoreInventory> cached = cache.get(storeId);
        if (cached.isPresent()) {
            return cached.get();
        }
        Store storeEntity = storesRepository.findById(storeId);
        StoreInventory built = build(storeId, storeEntity);
        cache.put(storeId, built, resolveTtl(storeEntity));
        return built;
    }

    public void invalidate(String storeId) {
        cache.invalidate(storeId);
    }

    private Duration resolveTtl(Store storeEntity) {
        return Optional.ofNullable(storeEntity)
                .flatMap(Store::getInventoryCacheTtlMinutes)
                .map(Duration::ofMinutes)
                .orElse(Duration.ofMinutes(defaultTtlMinutes));
    }

    private StoreInventory build(String storeId, Store storeEntity) {
        if (storeEntity == null) {
            return new StoreInventory(new ArrayList<>(), LocalDateTime.now());
        }

        List<InventoryItem> combined = new ArrayList<>();
        if (storeEntity.canUseGlobalSuppliers()) {
            combined.addAll(globalInventory.itemsForSuppliers(storeEntity.getGlobalSupplierNames()));
        }

        Map<String, Double> sellRates = exchangeRates.getCurrentSellRates();
        for (String supplierName : storeEntity.getOwnSupplierNames()) {
            supplierRegistry.getDescriptor(supplierName)
                    .ifPresent(descriptor -> combined.addAll(storeFeedItemLoader.load(storeId, descriptor, sellRates)));
        }

        return new StoreInventory(autoDiscovery.run(combined), LocalDateTime.now());
    }
}
