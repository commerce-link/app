package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStoreInventoryCacheTest {

    private final InMemoryStoreInventoryCache cache = new InMemoryStoreInventoryCache(100, 60);

    private StoreInventory anyInventory() {
        return new StoreInventory(new LinkedList<>(), LocalDateTime.now());
    }

    @Test
    void storesAndRetrievesByStoreId() {
        StoreInventory inventory = anyInventory();
        cache.put("store-1", inventory);

        assertEquals(inventory, cache.get("store-1").orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownStore() {
        assertTrue(cache.get("missing").isEmpty());
    }

    @Test
    void invalidateRemovesEntry() {
        cache.put("store-1", anyInventory());
        cache.invalidate("store-1");

        assertTrue(cache.get("store-1").isEmpty());
    }

    @Test
    void invalidateAllRemovesEveryEntry() {
        cache.put("store-1", anyInventory());
        cache.put("store-2", anyInventory());

        cache.invalidateAll();

        assertTrue(cache.get("store-1").isEmpty());
        assertTrue(cache.get("store-2").isEmpty());
    }
}
