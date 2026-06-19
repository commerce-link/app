package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStoreInventoryCacheTest {

    private final InMemoryStoreInventoryCache cache = new InMemoryStoreInventoryCache(100, 60);

    private StoreInventory anyInventory() {
        return new StoreInventory(new LinkedList<>(), LocalDateTime.now());
    }

    @Test
    void storesAndReturnsInventoryWithProvidedTtl() {
        // given
        StoreInventory inventory = new StoreInventory(new ArrayList<>(), LocalDateTime.now());

        // when
        cache.put("store-1", inventory, Duration.ofMinutes(15));

        // then
        assertThat(cache.get("store-1")).contains(inventory);
    }

    @Test
    void storesAndRetrievesByStoreId() {
        // given
        StoreInventory inventory = anyInventory();
        cache.put("store-1", inventory, Duration.ofMinutes(60));

        // when / then
        assertEquals(inventory, cache.get("store-1").orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownStore() {
        // when / then
        assertTrue(cache.get("missing").isEmpty());
    }

}
