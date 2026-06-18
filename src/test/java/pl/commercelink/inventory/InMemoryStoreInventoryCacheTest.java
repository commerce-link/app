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
        // given
        StoreInventory inventory = anyInventory();
        cache.put("store-1", inventory);

        // when / then
        assertEquals(inventory, cache.get("store-1").orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownStore() {
        // when / then
        assertTrue(cache.get("missing").isEmpty());
    }

    @Test
    void invalidateRemovesEntry() {
        // given
        cache.put("store-1", anyInventory());

        // when
        cache.invalidate("store-1");

        // then
        assertTrue(cache.get("store-1").isEmpty());
    }

    @Test
    void invalidateAllRemovesEveryEntry() {
        // given
        cache.put("store-1", anyInventory());
        cache.put("store-2", anyInventory());

        // when
        cache.invalidateAll();

        // then
        assertTrue(cache.get("store-1").isEmpty());
        assertTrue(cache.get("store-2").isEmpty());
    }
}
