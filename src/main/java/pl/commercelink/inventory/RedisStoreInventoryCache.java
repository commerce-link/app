package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "inventory.store-cache.type", havingValue = "redis")
public class RedisStoreInventoryCache implements StoreInventoryCache {

    private static final String GENERATION_KEY = "store-inventory:generation";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TaxonomyCache taxonomyCache;
    private final SupplierRegistry supplierRegistry;
    private final long ttlMinutes;

    public RedisStoreInventoryCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                    TaxonomyCache taxonomyCache, SupplierRegistry supplierRegistry,
                                    @Value("${inventory.store-cache.ttl-minutes:60}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.taxonomyCache = taxonomyCache;
        this.supplierRegistry = supplierRegistry;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public Optional<StoreInventory> get(String storeId) {
        try {
            String json = redisTemplate.opsForValue().get(key(storeId));
            if (json == null) {
                return Optional.empty();
            }
            StoreInventorySnapshot snapshot = objectMapper.readValue(json, StoreInventorySnapshot.class);
            return Optional.of(snapshot.toStoreInventory(taxonomyCache, supplierRegistry));
        } catch (Exception e) {
            System.err.println("Redis store-inventory get failed for " + storeId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String storeId, StoreInventory inventory) {
        try {
            String json = objectMapper.writeValueAsString(StoreInventorySnapshot.from(inventory));
            redisTemplate.opsForValue().set(key(storeId), json, Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            System.err.println("Redis store-inventory put failed for " + storeId + ": " + e.getMessage());
        }
    }

    @Override
    public void invalidate(String storeId) {
        try {
            redisTemplate.delete(key(storeId));
        } catch (Exception e) {
            System.err.println("Redis store-inventory invalidate failed for " + storeId + ": " + e.getMessage());
        }
    }

    @Override
    public void invalidateAll() {
        try {
            redisTemplate.opsForValue().increment(GENERATION_KEY);
        } catch (Exception e) {
            System.err.println("Redis store-inventory invalidateAll failed: " + e.getMessage());
        }
    }

    private String key(String storeId) {
        String generation = redisTemplate.opsForValue().get(GENERATION_KEY);
        return "store-inventory:" + (generation == null ? "0" : generation) + ":" + storeId;
    }
}
