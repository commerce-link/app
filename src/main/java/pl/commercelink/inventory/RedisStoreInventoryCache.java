package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    public void put(String storeId, StoreInventory inventory, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(StoreInventorySnapshot.from(inventory));
            redisTemplate.opsForValue().set(key(storeId), json, ttl);
        } catch (Exception e) {
            System.err.println("Redis store-inventory put failed for " + storeId + ": " + e.getMessage());
        }
    }

    private String key(String storeId) {
        return "store-inventory:" + storeId;
    }
}
