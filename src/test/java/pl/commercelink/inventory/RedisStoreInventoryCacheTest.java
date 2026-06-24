package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisStoreInventoryCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private TaxonomyCache taxonomyCache;
    @Mock private SupplierRegistry supplierRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private RedisStoreInventoryCache cache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new RedisStoreInventoryCache(redisTemplate, objectMapper, taxonomyCache, supplierRegistry);
    }

    private StoreInventory sampleInventory() {
        InventoryItem item = new InventoryItem("5900000000002", "MFN-1", 10.0, "PLN", 5, 1, "Acme", true, true, false);
        MatchedInventory matched = new MatchedInventory(new InventoryKey("5900000000002", "MFN-1"),
                List.of(item), taxonomyCache, supplierRegistry);
        return new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

    @Test
    void getReturnsEmptyOnMiss() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:s1")).thenReturn(null);

        // when / then
        assertTrue(cache.get("s1").isEmpty());
    }

    @Test
    void getRehydratesStoredSnapshot() throws Exception {
        // given
        RedisStoreInventoryCache cache = cache();
        String json = objectMapper.writeValueAsString(StoreInventorySnapshot.from(sampleInventory()));
        when(valueOps.get("store-inventory:s1")).thenReturn(json);

        // when
        Optional<StoreInventory> result = cache.get("s1");

        // then
        assertTrue(result.isPresent());
        MatchedInventory m = result.get().items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
    }

    @Test
    void writesValueWithProvidedPerKeyTtl() {
        // given
        RedisStoreInventoryCache cache = cache();
        StoreInventory inventory = new StoreInventory(InventoryIndex.of(new ArrayList<>()), LocalDateTime.now());

        // when
        cache.put("store-1", inventory, Duration.ofMinutes(15));

        // then
        verify(valueOps).set(eq("store-inventory:store-1"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void getDegradesToMissWhenRedisFails() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:s1")).thenThrow(new RuntimeException("redis down"));

        // when / then
        assertTrue(cache.get("s1").isEmpty());
    }

    @Test
    void putSwallowsRedisFailure() {
        // given
        RedisStoreInventoryCache cache = cache();
        doThrow(new RuntimeException("redis down")).when(valueOps)
                .set(eq("store-inventory:s1"), anyString(), eq(Duration.ofMinutes(60)));

        // when / then
        assertDoesNotThrow(() -> cache.put("s1", sampleInventory(), Duration.ofMinutes(60)));
    }

}
