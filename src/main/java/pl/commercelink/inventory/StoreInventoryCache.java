package pl.commercelink.inventory;

import java.time.Duration;
import java.util.Optional;

public interface StoreInventoryCache {

    Optional<StoreInventory> get(String storeId);

    /**
     * The time of the store shared by every instance, to be read before a build starts and passed to {@link #put}.
     */
    long clockMillis();

    /**
     * Stores the inventory unless one whose build started later is already stored, or the store's inventory was
     * evicted after this build started.
     */
    void put(String storeId, StoreInventory inventory, Duration ttl, long buildStartedAtMillis);

    /**
     * Gives the stored inventory its lifetime again when its build started less than {@code maxAge} ago.
     *
     * @return false when nothing was kept: no inventory is stored, it is older, or its age is unknown
     */
    boolean keepIfBuiltWithin(String storeId, Duration maxAge, Duration ttl);

    void evict(String storeId);
}
