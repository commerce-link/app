package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
@Slf4j
public class RedisStoreInventoryCache implements StoreInventoryCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SupplierRegistry supplierRegistry;

    public RedisStoreInventoryCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                    SupplierRegistry supplierRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
            return Optional.of(snapshot.toStoreInventory(supplierRegistry));
        } catch (Exception e) {
            log.warn("Redis store-inventory get failed for {}: {}", storeId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String storeId, StoreInventory inventory, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(StoreInventorySnapshot.from(inventory));
            redisTemplate.opsForValue().set(key(storeId), compress(json), ttl);
        } catch (Exception e) {
            log.warn("Redis store-inventory put failed for {}: {}", storeId, e.getMessage());
        }
    }

    @Override
    public void evict(String storeId) {
        try {
            redisTemplate.delete(key(storeId));
        } catch (Exception e) {
            log.warn("Redis store-inventory evict failed for {}: {}", storeId, e.getMessage());
        }
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

    private String key(String storeId) {
        return "store-inventory:v2:" + storeId;
    }
}
