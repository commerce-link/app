package pl.commercelink.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.commercelink.products.information.PimIndexRefreshed;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

/**
 * After every PIM index refresh, regroups the global pool when the pairs flagged in PIM to join by EAN only differ from
 * the ones the pool was grouped with, then drops the cached inventory of every store so it is rebuilt on the new pairs.
 * A feed reload also regroups with the current pairs, so the eviction follows the pairs of the global pool, not only
 * the regroups made here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EanOnlyCodesWatch {

    private final EanOnlyCodes eanOnlyCodes;
    private final GlobalMatchedInventory globalInventory;
    private final Inventory inventory;
    private final StoreInventoryCache storeInventoryCache;
    private final InventoryMatchingProperties matchingProperties;
    private final StoresRepository storesRepository;

    // the pairs of the global pool the store caches were last evicted for; set on the first event after the first
    // global load, when the store caches can only have been built on the pool's own pairs
    private volatile VariantCodes lastEvictedFor;

    @EventListener
    void onPimIndexRefreshed(PimIndexRefreshed event) {
        VariantCodes current = eanOnlyCodes.refresh();
        if (!matchingProperties.pimEanOnlyFlag() || globalInventory.version() == 0) {
            return;
        }
        VariantCodes before = globalInventory.generation().eanOnlyCodes();
        if (lastEvictedFor == null) {
            lastEvictedFor = before;
        }
        boolean regrouped = !current.equals(before);
        if (regrouped) {
            inventory.regroup();
        }

        VariantCodes grouped = globalInventory.generation().eanOnlyCodes();
        if (grouped.equals(lastEvictedFor)) {
            return;
        }
        // after the regroup: a store rebuilt in between would take the old global groups and keep them cached
        List<String> storeIds;
        try {
            storeIds = storesRepository.findAllStoreIds();
        } catch (RuntimeException e) {
            // lastEvictedFor stays, so the next refresh tries the eviction again
            log.warn("Store inventory caches not evicted after the PIM EAN-only flags changed, retrying on the next PIM refresh: {}",
                    e.toString());
            return;
        }
        int evicted = evictAll(storeIds);
        lastEvictedFor = grouped;

        if (regrouped) {
            log.info("Regrouped the global inventory after the PIM EAN-only flags changed: {} pairs added, {} removed; evicted the inventory cache of {} stores",
                    current.countNotIn(before), before.countNotIn(current), evicted);
        } else {
            log.info("The global inventory was already regrouped with changed PIM EAN-only flags; evicted the inventory cache of {} stores",
                    evicted);
        }
    }

    private int evictAll(List<String> storeIds) {
        int evicted = 0;
        for (String storeId : storeIds) {
            try {
                storeInventoryCache.evict(storeId);
                evicted++;
            } catch (RuntimeException e) {
                log.warn("Inventory cache of store {} not evicted after the PIM EAN-only flags changed: {}", storeId, e.toString());
            }
        }
        return evicted;
    }
}
