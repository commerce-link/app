package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStoreInventoryStoreTest {

    private final InMemoryStoreInventoryStore store = new InMemoryStoreInventoryStore(100, 60);

    private StoreInventory anyInventory() {
        return new StoreInventory(new LinkedList<>(), LocalDateTime.now());
    }

    @Test
    void storesAndRetrievesByStoreId() {
        StoreInventory inventory = anyInventory();
        store.put("store-1", inventory);

        assertEquals(inventory, store.get("store-1").orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownStore() {
        assertTrue(store.get("missing").isEmpty());
    }

    @Test
    void invalidateRemovesEntry() {
        store.put("store-1", anyInventory());
        store.invalidate("store-1");

        assertTrue(store.get("store-1").isEmpty());
    }

    @Test
    void invalidateAllRemovesEveryEntry() {
        store.put("store-1", anyInventory());
        store.put("store-2", anyInventory());

        store.invalidateAll();

        assertTrue(store.get("store-1").isEmpty());
        assertTrue(store.get("store-2").isEmpty());
    }
}
