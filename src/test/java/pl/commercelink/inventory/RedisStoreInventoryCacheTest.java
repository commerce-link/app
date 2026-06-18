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
        return new RedisStoreInventoryCache(redisTemplate, objectMapper, taxonomyCache, supplierRegistry, 60);
    }

    private StoreInventory sampleInventory() {
        InventoryItem item = new InventoryItem("5900000000002", "MFN-1", 10.0, "PLN", 5, 1, "Acme", true, true, false);
        MatchedInventory matched = new MatchedInventory(new InventoryKey("5900000000002", "MFN-1"),
                List.of(item), taxonomyCache, supplierRegistry);
        return new StoreInventory(List.of(matched), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

    @Test
    void getReturnsEmptyOnMiss() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn("0");
        when(valueOps.get("store-inventory:0:s1")).thenReturn(null);

        // when / then
        assertTrue(cache.get("s1").isEmpty());
    }

    @Test
    void getRehydratesStoredSnapshot() throws Exception {
        // given
        RedisStoreInventoryCache cache = cache();
        String json = objectMapper.writeValueAsString(StoreInventorySnapshot.from(sampleInventory()));
        when(valueOps.get("store-inventory:generation")).thenReturn("0");
        when(valueOps.get("store-inventory:0:s1")).thenReturn(json);

        // when
        Optional<StoreInventory> result = cache.get("s1");

        // then
        assertTrue(result.isPresent());
        MatchedInventory m = result.get().items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
    }

    @Test
    void putWritesSnapshotWithTtlUnderGenerationKey() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn("0");

        // when
        cache.put("s1", sampleInventory());

        // then
        verify(valueOps).set(eq("store-inventory:0:s1"), anyString(), eq(Duration.ofMinutes(60)));
    }

    @Test
    void invalidateDeletesGenerationKey() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn("0");

        // when
        cache.invalidate("s1");

        // then
        verify(redisTemplate).delete("store-inventory:0:s1");
    }

    @Test
    void invalidateAllIncrementsGeneration() {
        // given
        RedisStoreInventoryCache cache = cache();

        // when
        cache.invalidateAll();

        // then
        verify(valueOps).increment("store-inventory:generation");
    }

    @Test
    void getDegradesToMissWhenRedisFails() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenThrow(new RuntimeException("redis down"));

        // when / then
        assertTrue(cache.get("s1").isEmpty());
    }

    @Test
    void getUsesGenerationZeroWhenGenerationKeyUnset() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn(null);

        // when
        cache.get("s1");

        // then
        verify(valueOps).get("store-inventory:0:s1");
    }

    @Test
    void bumpedGenerationChangesTheResolvedKey() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn("0");

        // when
        cache.get("s1");
        cache.put("s1", sampleInventory());

        // then
        verify(valueOps).get("store-inventory:0:s1");
        verify(valueOps).set(eq("store-inventory:0:s1"), anyString(), eq(Duration.ofMinutes(60)));

        // given
        when(valueOps.get("store-inventory:generation")).thenReturn("1");

        // when
        cache.get("s1");
        cache.put("s1", sampleInventory());

        // then
        verify(valueOps).get("store-inventory:1:s1");
        verify(valueOps).set(eq("store-inventory:1:s1"), anyString(), eq(Duration.ofMinutes(60)));
    }

    @Test
    void putSwallowsRedisFailure() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn("0");
        doThrow(new RuntimeException("redis down")).when(valueOps)
                .set(eq("store-inventory:0:s1"), anyString(), eq(Duration.ofMinutes(60)));

        // when / then
        assertDoesNotThrow(() -> cache.put("s1", sampleInventory()));
    }

    @Test
    void invalidateSwallowsRedisFailure() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:generation")).thenReturn("0");
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete("store-inventory:0:s1");

        // when / then
        assertDoesNotThrow(() -> cache.invalidate("s1"));
    }

    @Test
    void invalidateAllSwallowsRedisFailure() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.increment("store-inventory:generation")).thenThrow(new RuntimeException("redis down"));

        // when / then
        assertDoesNotThrow(() -> cache.invalidateAll());
    }
}
