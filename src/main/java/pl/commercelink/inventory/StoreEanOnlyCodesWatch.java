package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.commercelink.products.information.PimIndexRefreshed;
import pl.commercelink.stores.StoresRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * After every PIM index refresh, drops the cached inventory of each store whose own entries flagged in PIM to join by
 * EAN only now give other pairs than at the previous refresh. A flag change sends no PIM event, so this comparison is
 * how a store's cache learns of it. The global pool does not depend on store entries and is left to
 * {@link EanOnlyCodesWatch}.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class StoreEanOnlyCodesWatch {

    private final StoreEanOnlyCodes storeEanOnlyCodes;
    private final StoreInventoryCache storeInventoryCache;
    private final InventoryMatchingProperties matchingProperties;
    private final StoresRepository storesRepository;

    // the store pairs the caches were last evicted for, only stores with pairs; null until the application is ready
    // (or, if that failed, until the first refresh), always replaced as a whole because the ready event and the
    // refresh listener run on different threads
    private volatile Map<String, VariantCodes> lastPairs;

    // The index the store caches are built on is loaded at startup, while the first refresh comes minutes later. The
    // baseline is taken here so that a flag changed in between is seen by that first refresh.
    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        if (!matchingProperties.pimEanOnlyFlag() || !matchingProperties.pimStoreEanOnlyFlag()) {
            return;
        }
        try {
            lastPairs = currentPairs();
        } catch (RuntimeException e) {
            // the first PIM refresh takes the baseline instead
            log.warn("PIM EAN-only flags of the stores not recorded at startup, taking them on the first PIM refresh: {}", e.toString());
        }
    }

    @EventListener
    void onPimIndexRefreshed(PimIndexRefreshed event) {
        if (!matchingProperties.pimEanOnlyFlag() || !matchingProperties.pimStoreEanOnlyFlag()) {
            // switching back on starts from a fresh baseline instead of comparing with pairs from before
            lastPairs = null;
            return;
        }
        Map<String, VariantCodes> current;
        try {
            current = currentPairs();
        } catch (RuntimeException e) {
            // lastPairs stays, so the next refresh compares with it again
            log.warn("PIM EAN-only flags of the stores not checked, retrying on the next PIM refresh: {}", e.toString());
            return;
        }
        Map<String, VariantCodes> previous = lastPairs;
        if (previous == null) {
            lastPairs = current;
            return;
        }

        Map<String, VariantCodes> next = new HashMap<>(current);
        Set<String> storeIds = new HashSet<>(previous.keySet());
        storeIds.addAll(current.keySet());
        int evicted = 0;
        for (String storeId : storeIds) {
            VariantCodes before = previous.getOrDefault(storeId, VariantCodes.none());
            if (before.equals(current.getOrDefault(storeId, VariantCodes.none()))) {
                continue;
            }
            try {
                storeInventoryCache.evict(storeId);
                evicted++;
            } catch (RuntimeException e) {
                // keeping the old pairs makes the next refresh see the change and evict again
                keep(next, storeId, before);
                log.warn("Inventory cache of store {} not evicted after its PIM EAN-only flags changed, retrying on the next PIM refresh: {}",
                        storeId, e.toString());
            }
        }
        lastPairs = next;
        if (evicted > 0) {
            log.info("Evicted the inventory cache of {} stores after their PIM EAN-only flags changed", evicted);
        }
    }

    private Map<String, VariantCodes> currentPairs() {
        List<String> storeIds = storesRepository.findAllStoreIds();
        Map<String, VariantCodes> pairs = new HashMap<>();
        for (String storeId : storeIds) {
            VariantCodes codes = storeEanOnlyCodes.of(storeId);
            if (codes.size() > 0) {
                pairs.put(storeId, codes);
            }
        }
        return pairs;
    }

    private static void keep(Map<String, VariantCodes> pairs, String storeId, VariantCodes codes) {
        if (codes.size() > 0) {
            pairs.put(storeId, codes);
        } else {
            pairs.remove(storeId);
        }
    }
}
