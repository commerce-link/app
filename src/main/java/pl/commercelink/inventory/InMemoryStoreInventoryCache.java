package pl.commercelink.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "inventory.store-cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryStoreInventoryCache implements StoreInventoryCache {

    private record CachedEntry(StoreInventory inventory, Duration ttl) {
    }

    private final Cache<String, CachedEntry> cache;

    public InMemoryStoreInventoryCache(
            @Value("${inventory.store-cache.max-size:100}") long maxSize,
            @Value("${inventory.store-cache.ttl-minutes:60}") long ttlMinutes) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new Expiry<String, CachedEntry>() {
                    @Override
                    public long expireAfterCreate(String key, CachedEntry value, long currentTime) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedEntry value, long currentTime, long currentDuration) {
                        return value.ttl().toNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, CachedEntry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public Optional<StoreInventory> get(String storeId) {
        return Optional.ofNullable(cache.getIfPresent(storeId)).map(CachedEntry::inventory);
    }

    @Override
    public void put(String storeId, StoreInventory inventory, Duration ttl) {
        cache.put(storeId, new CachedEntry(inventory, ttl));
    }

    @Override
    public void invalidate(String storeId) {
        cache.invalidate(storeId);
    }
}
