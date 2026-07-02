package pl.commercelink.inventory;

import java.time.Duration;
import java.util.Optional;

public interface StoreInventoryCache {

    Optional<StoreInventory> get(String storeId);

    void put(String storeId, StoreInventory inventory, Duration ttl);

    void evict(String storeId);
}
