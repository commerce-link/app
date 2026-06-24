package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisStoreInventoryCacheIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redisTemplate;

    private final TaxonomyCache taxonomyCache = Mockito.mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = Mockito.mock(SupplierRegistry.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @BeforeAll
    static void startTemplate() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopTemplate() {
        factory.destroy();
    }

    @BeforeEach
    void flush() {
        try (RedisConnection connection = factory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    private RedisStoreInventoryCache cache() {
        return new RedisStoreInventoryCache(redisTemplate, objectMapper, taxonomyCache, supplierRegistry);
    }

    private StoreInventory sampleInventory() {
        InventoryItem item = new InventoryItem("5900000000002", "MFN-1", 10.0, "PLN", 5, 1, "Acme", true, true, false);
        MatchedInventory matched = new MatchedInventory(new InventoryKey("5900000000002", "MFN-1"),
                List.of(item), taxonomyCache, supplierRegistry);
        return new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

    private StoreInventory largeInventory(int count) {
        List<MatchedInventory> matched = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String ean = String.format("59%011d", i);
            String mfn = "MFN-" + i;
            List<InventoryItem> items = List.of(
                    new InventoryItem(ean, mfn, 10.0 + i, "PLN", 5, 1, "Acme", true, true, false),
                    new InventoryItem(ean, mfn, 11.0 + i, "PLN", 3, 2, "Beta", true, false, true),
                    new InventoryItem(ean, mfn, 12.0 + i, "PLN", 7, 1, "Gamma", true, true, false));
            matched.add(new MatchedInventory(new InventoryKey(ean, mfn), items, taxonomyCache, supplierRegistry));
        }
        return new StoreInventory(InventoryIndex.of(matched), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

    @Test
    void putThenGetRoundTripsThroughRealRedis() {
        // given
        RedisStoreInventoryCache cache = cache();

        // when
        cache.put("s1", sampleInventory(), Duration.ofMinutes(60));
        Optional<StoreInventory> result = cache.get("s1");

        // then
        assertTrue(result.isPresent());
        MatchedInventory m = result.get().items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
    }

    @Test
    void putAppliesProvidedTtlOnTheRealKey() {
        // given
        RedisStoreInventoryCache cache = cache();

        // when
        cache.put("s1", sampleInventory(), Duration.ofMinutes(60));

        // then
        Long ttl = redisTemplate.getExpire("store-inventory:s1");
        assertTrue(ttl != null && ttl > 0 && ttl <= 3600);
    }

    @Test
    void getReturnsEmptyForUnknownStore() {
        // given
        RedisStoreInventoryCache cache = cache();

        // when / then
        assertTrue(cache.get("does-not-exist").isEmpty());
    }

    @Test
    void largeInventoryRoundTripsAndStaysUnderSizeCeiling() throws Exception {
        // given
        RedisStoreInventoryCache cache = cache();
        int entries = 50_000;
        StoreInventory large = largeInventory(entries);
        int rawJsonBytes = objectMapper.writeValueAsString(StoreInventorySnapshot.from(large))
                .getBytes(StandardCharsets.UTF_8).length;

        // when
        cache.put("big", large, Duration.ofMinutes(60));
        Optional<StoreInventory> result = cache.get("big");

        // then
        assertTrue(result.isPresent());
        assertEquals(entries, result.get().items().size());
        int storedBytes = redisTemplate.opsForValue().get("store-inventory:big")
                .getBytes(StandardCharsets.UTF_8).length;
        assertTrue(storedBytes < rawJsonBytes);
        assertTrue(storedBytes < 64 * 1024 * 1024);
    }
}
