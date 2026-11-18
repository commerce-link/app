package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisStoreInventoryCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private TaxonomyCache taxonomyCache;
    @Mock private SupplierRegistry supplierRegistry;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

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
        when(valueOps.get("store-inventory:v3:s1")).thenReturn(null);

        // when / then
        assertTrue(cache.get("s1").isEmpty());
    }

    @Test
    void getRehydratesStoredSnapshot() {
        // given
        RedisStoreInventoryCache cache = cache();
        cache.put("s1", sampleInventory(), Duration.ofMinutes(60), 1_000L);
        String stored = storedPayload();
        when(valueOps.get("store-inventory:v3:s1")).thenReturn(stored);

        // when
        Optional<StoreInventory> result = cache.get("s1");

        // then
        assertTrue(result.isPresent());
        MatchedInventory m = result.get().items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
    }

    @Test
    void putSendsTheBuildStartAndBothLifetimesWithTheSnapshot() {
        // given
        RedisStoreInventoryCache cache = cache();
        StoreInventory inventory = new StoreInventory(InventoryIndex.of(new ArrayList<>()), LocalDateTime.now());

        // when
        cache.put("store-1", inventory, Duration.ofMinutes(2880), 1_700_000_000_123L);

        // then
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("store-inventory:v3:store-1", "store-inventory:v3:store-1:build-started")),
                eq("1700000000123"), anyString(), eq("172800000"), eq("172800000"));
    }

    @Test
    void buildStartMarkOfAShortLivedSnapshotStillLivesAnHour() {
        // given
        RedisStoreInventoryCache cache = cache();
        StoreInventory inventory = new StoreInventory(InventoryIndex.of(new ArrayList<>()), LocalDateTime.now());

        // when
        cache.put("store-1", inventory, Duration.ofMinutes(15), 5L);

        // then
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("5"), anyString(), eq("900000"), eq("3600000"));
    }

    @Test
    void evictMarksTheStoreBeforeDeletingTheSnapshot() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(4_242L);

        // when
        cache.evict("s1");

        // then
        InOrder order = inOrder(valueOps, redisTemplate);
        order.verify(valueOps).set("store-inventory:v3:s1:build-started", "4242", Duration.ofHours(1));
        order.verify(redisTemplate).delete("store-inventory:v3:s1");
    }

    @Test
    void clockIsReadFromRedis() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(4_242L);

        // when / then
        assertEquals(4_242L, cache.clockMillis());
    }

    @Test
    void clockFallsBackToTheInstanceWhenRedisFails() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("redis down"));
        long before = System.currentTimeMillis();

        // when
        long clock = cache.clockMillis();

        // then
        assertTrue(clock >= before);
    }

    @Test
    void builtSinceAsksTheScriptWithTheSnapshotAndItsMark() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        // when
        boolean built = cache.builtSince("s1", 4_242L);

        // then
        assertTrue(built);
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(List.of("store-inventory:v3:s1", "store-inventory:v3:s1:build-started")), eq("4242"));
    }

    @Test
    void builtSinceIsFalseWhenRedisFails() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        // when / then
        org.junit.jupiter.api.Assertions.assertFalse(cache.builtSince("s1", 4_242L));
    }

    @Test
    void getDegradesToMissWhenRedisFails() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(valueOps.get("store-inventory:v3:s1")).thenThrow(new RuntimeException("redis down"));

        // when / then
        assertTrue(cache.get("s1").isEmpty());
    }

    @Test
    void putSwallowsRedisFailure() {
        // given
        RedisStoreInventoryCache cache = cache();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));

        // when / then
        assertDoesNotThrow(() -> cache.put("s1", sampleInventory(), Duration.ofMinutes(60), 1L));
    }

    @Test
    void storesCompressedPayloadSmallerThanRawJson() throws Exception {
        // given
        RedisStoreInventoryCache cache = cache();
        StoreInventory inventory = largeInventory(2000);
        String rawJson = objectMapper.writeValueAsString(StoreInventorySnapshot.from(inventory));

        // when
        cache.put("s1", inventory, Duration.ofMinutes(60), 1L);

        // then
        assertTrue(storedPayload().length() < rawJson.length());
    }

    private String storedPayload() {
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), arguments.capture());
        return (String) arguments.getValue()[1];
    }

    private StoreInventory largeInventory(int count) {
        List<MatchedInventory> matched = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String ean = String.format("59%011d", i);
            String mfn = "MFN-" + i;
            InventoryItem item = new InventoryItem(ean, mfn, 10.0 + i, "PLN", 5, 1, "Acme", true, true, false);
            matched.add(new MatchedInventory(new InventoryKey(ean, mfn), List.of(item), taxonomyCache, supplierRegistry));
        }
        return new StoreInventory(InventoryIndex.of(matched), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

}
