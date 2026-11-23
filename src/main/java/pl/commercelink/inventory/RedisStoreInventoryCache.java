package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
public class RedisStoreInventoryCache implements StoreInventoryCache {

    private static final Duration MIN_MARK_LIFETIME = Duration.ofHours(1);

    // Builds of one store run at the same time on several instances and in several threads (a feed import, a request
    // that found no snapshot), and the one that finishes last is not the one that read the newest files. The one
    // that started last is: every feed write and every eviction is followed by a build that starts after it.
    private static final RedisScript<Long> PUT_UNLESS_SUPERSEDED = new DefaultRedisScript<>("""
            local mark = redis.call('GET', KEYS[2])
            if mark and tonumber(mark) > tonumber(ARGV[1]) then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[4])
            return 1
            """, Long.class);

    // an eviction sets the mark without a snapshot, hence EXISTS
    private static final RedisScript<Long> BUILT_SINCE = new DefaultRedisScript<>("""
            local mark = redis.call('GET', KEYS[2])
            if not mark or tonumber(mark) < tonumber(ARGV[1]) then
                return 0
            end
            return redis.call('EXISTS', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;

    public RedisStoreInventoryCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                    TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
    }

    @Override
    public Optional<StoreInventory> get(String storeId) {
        try {
            String stored = redisTemplate.opsForValue().get(key(storeId));
            if (stored == null) {
                return Optional.empty();
            }
            StoreInventorySnapshot snapshot = objectMapper.readValue(decompress(stored), StoreInventorySnapshot.class);
            return Optional.of(snapshot.toStoreInventory(taxonomyCache, supplierRegistry));
        } catch (Exception e) {
            System.err.println("Redis store-inventory get failed for " + storeId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public long clockMillis() {
        try {
            Long millis = redisTemplate.execute((RedisCallback<Long>) connection -> connection.serverCommands().time());
            if (millis != null) {
                return millis;
            }
        } catch (Exception e) {
            log.warn("Redis time is unavailable, the build of a store inventory is timed by this instance's clock", e);
        }
        return System.currentTimeMillis();
    }

    @Override
    public void put(String storeId, StoreInventory inventory, Duration ttl, long buildStartedAtMillis) {
        try {
            String json = objectMapper.writeValueAsString(StoreInventorySnapshot.from(inventory));
            redisTemplate.execute(PUT_UNLESS_SUPERSEDED, List.of(key(storeId), buildStartKey(storeId)),
                    String.valueOf(buildStartedAtMillis), compress(json),
                    String.valueOf(ttl.toMillis()), String.valueOf(markLifetime(ttl).toMillis()));
        } catch (Exception e) {
            throw new StoreInventoryCacheException("Inventory snapshot of store " + storeId + " was not stored", e);
        }
    }

    @Override
    public boolean builtSince(String storeId, long sinceMillis) {
        try {
            Long built = redisTemplate.execute(BUILT_SINCE, List.of(key(storeId), buildStartKey(storeId)),
                    String.valueOf(sinceMillis));
            return built != null && built == 1;
        } catch (Exception e) {
            log.warn("The build time of the store inventory snapshot of {} could not be read, it is rebuilt", storeId, e);
            return false;
        }
    }

    @Override
    public void evict(String storeId) {
        try {
            // the mark first: a build that started before the eviction is refused from then on, also one that
            // finishes between these two commands
            redisTemplate.opsForValue().set(buildStartKey(storeId), String.valueOf(clockMillis()), MIN_MARK_LIFETIME);
            redisTemplate.delete(key(storeId));
        } catch (Exception e) {
            System.err.println("Redis store-inventory evict failed for " + storeId + ": " + e.getMessage());
        }
    }

    // a mark only has to outlive the builds that were running when it was set
    private Duration markLifetime(Duration ttl) {
        return ttl.compareTo(MIN_MARK_LIFETIME) > 0 ? ttl : MIN_MARK_LIFETIME;
    }

    private String compress(String json) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private String decompress(String stored) throws IOException {
        byte[] compressed = Base64.getDecoder().decode(stored);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // the version changes with the grouping of offers (v3: links of own groups to global groups), so a snapshot
    // grouped the old way is never read back
    private String key(String storeId) {
        return "store-inventory:v3:" + storeId;
    }

    private String buildStartKey(String storeId) {
        return key(storeId) + ":build-started";
    }
}
