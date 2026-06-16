package pl.commercelink.inventory;

import java.util.Optional;

public interface StoreInventoryStore {

    Optional<StoreInventory> get(String storeId);

    void put(String storeId, StoreInventory inventory);

    void invalidate(String storeId);

    void invalidateAll();
}
