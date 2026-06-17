package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StoreInventoryProvider {

    private final StoreInventoryCache cache;
    private final StoresRepository storesRepository;
    private final Inventory inventory;
    private final SupplierRegistry supplierRegistry;
    private final InventoryAutoDiscovery autoDiscovery;
    private final StoreFeedItemLoader storeFeedItemLoader;
    private final ExchangeRates exchangeRates;

    public StoreInventory get(String storeId) {
        return cache.get(storeId).orElseGet(() -> {
            StoreInventory built = build(storeId);
            cache.put(storeId, built);
            return built;
        });
    }

    public void invalidate(String storeId) {
        cache.invalidate(storeId);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private StoreInventory build(String storeId) {
        Store storeEntity = storesRepository.findById(storeId);
        if (storeEntity == null) {
            return new StoreInventory(new ArrayList<>(), LocalDateTime.now());
        }

        List<InventoryItem> combined = new ArrayList<>();
        if (storeEntity.canUseGlobalSuppliers()) {
            combined.addAll(inventory.globalItemsForSuppliers(storeEntity.getGlobalSupplierNames()));
        }

        Map<String, Double> sellRates = exchangeRates.getCurrentSellRates();
        for (String supplierName : storeEntity.getOwnSupplierNames()) {
            supplierRegistry.getDescriptor(supplierName)
                    .ifPresent(descriptor -> combined.addAll(storeFeedItemLoader.load(storeId, descriptor, sellRates)));
        }

        return new StoreInventory(autoDiscovery.run(combined), LocalDateTime.now());
    }
}
