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
     * evicted after this build started. Neither is a failure.
     *
     * @throws StoreInventoryCacheException when the inventory could not be written; unlike a failed read or eviction
     *                                      this is not swallowed, since the caller decides whether to build again
     */
    void put(String storeId, StoreInventory inventory, Duration ttl, long buildStartedAtMillis);

    /**
     * Whether an inventory is stored whose build started at {@code sinceMillis} of {@link #clockMillis} or later.
     */
    boolean builtSince(String storeId, long sinceMillis);

    /**
     * Gives the stored inventory its lifetime again when its build started less than {@code maxAge} ago.
     *
     * @return false when nothing was kept: no inventory is stored, it is older, or its age is unknown
     */
    boolean keepIfBuiltWithin(String storeId, Duration maxAge, Duration ttl);

    void evict(String storeId);
}
