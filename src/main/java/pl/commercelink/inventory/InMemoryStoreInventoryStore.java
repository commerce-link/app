package pl.commercelink.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class InMemoryStoreInventoryStore implements StoreInventoryStore {

    private final Cache<String, StoreInventory> cache;

    public InMemoryStoreInventoryStore(
            @Value("${inventory.store-cache.max-size:100}") long maxSize,
            @Value("${inventory.store-cache.ttl-minutes:60}") long ttlMinutes) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .build();
    }

    @Override
    public Optional<StoreInventory> get(String storeId) {
        return Optional.ofNullable(cache.getIfPresent(storeId));
    }

    @Override
    public void put(String storeId, StoreInventory inventory) {
        cache.put(storeId, inventory);
    }

    @Override
    public void invalidate(String storeId) {
        cache.invalidate(storeId);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
