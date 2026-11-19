package pl.commercelink.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.financials.ExchangeRates;
import pl.commercelink.inventory.supplier.StoreFeedItemLoader;
import pl.commercelink.inventory.supplier.SupplierProviderFactory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.api.SupplierProviderDescriptor;
import pl.commercelink.inventory.supplier.manual.ManualSupplierDescriptor;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class StoreInventoryProvider {

    private static final Duration MAX_AGE_OF_KEPT_SNAPSHOT = Duration.ofHours(24);
    private static final int REFRESH_ATTEMPTS = 3;

    private final StoreInventoryCache cache;
    private final StoresRepository storesRepository;
    private final SupplierProviderFactory supplierProviderFactory;
    private final InventoryAutoDiscovery autoDiscovery;
    private final StoreFeedItemLoader storeFeedItemLoader;
    private final ExchangeRates exchangeRates;
    private final PimCatalog pimCatalog;
    private final GlobalMatchedInventory globalInventory;
    private final InventoryMatchingProperties matchingProperties;
    private final StoreEanOnlyCodes storeEanOnlyCodes;

    @Value("${inventory.store-cache.ttl-minutes}")
    long defaultTtlMinutes;

    public StoreInventory get(String storeId) {
        return cache.get(storeId).orElseGet(() -> buildAndCache(storesRepository.findById(storeId), storeId).inventory());
    }

    public InventoryIndex ownIndex(Store store) {
        return ownInventory(store).index();
    }

    public StoreInventory ownInventory(Store store) {
        if (store == null || !store.hasOwnOrManualSupplierConnections()) {
            return new StoreInventory(InventoryIndex.of(List.of()), null);
        }
        String storeId = store.getStoreId();
        return cache.get(storeId).orElseGet(() -> buildAndCache(store, storeId).inventory());
    }

    // Each feed import of a store asks for a rebuild. A build that started after a request has already read that
    // import's file, so the request has nothing left to do; null rebuilds whatever is stored.
    public void refresh(String storeId, Long requestedAtMillis) {
        if (requestedAtMillis != null && cache.builtSince(storeId, requestedAtMillis)) {
            return;
        }
        Store store = storesRepository.findById(storeId);
        if (store == null) {
            log.warn("Inventory snapshot of store {} is not rebuilt: the store does not exist", storeId);
            return;
        }
        // a build that met a global reload or a change of the store's EAN-only pairs is not cached; the next one starts
        // on the current ones
        for (int attempt = 0; attempt < REFRESH_ATTEMPTS; attempt++) {
            if (buildAndCache(store, storeId).cached()) {
                return;
            }
        }
        throw new IllegalStateException("The global inventory or the store's EAN-only pairs changed during each of "
                + REFRESH_ATTEMPTS + " rebuilds of the inventory of store " + storeId);
    }

    // For an import that found its feed unchanged: the snapshot only lives on, without a rebuild. Not past a day
    // though, since its prices carry the exchange rates of the day it was built; false asks the caller for a rebuild.
    public boolean keepIfRecent(String storeId) {
        Store store = storesRepository.findById(storeId);
        return cache.keepIfBuiltWithin(storeId, MAX_AGE_OF_KEPT_SNAPSHOT, resolveTtl(store));
    }

    private Built buildAndCache(Store store, String storeId) {
        // read before the feed files are: the cache keeps the inventory of the build that started last
        long startedAt = cache.clockMillis();
        Build build = build(storeId, store);
        // a global reload during the build (new feeds, or a regroup on changed EAN-only pairs that has already evicted
        // the stores) makes these groups stale: they serve this request, but caching them would keep them for the TTL;
        // so do store pairs changed in PIM meanwhile, whose eviction may already have run
        boolean sameGlobal = build.globalVersion() == null || build.globalVersion() == globalInventory.version();
        boolean sameStorePairs = build.storePairs() == null || build.storePairs().equals(storeEanOnlyCodes.of(storeId));
        boolean current = sameGlobal && sameStorePairs;
        if (current) {
            cache.put(storeId, build.inventory(), resolveTtl(store), startedAt);
        }
        return new Built(build.inventory(), current);
    }

    private record Built(StoreInventory inventory, boolean cached) {
    }

    /**
     * @param globalVersion the version of the global generation the build used, null when it used none
     * @param storePairs    the store's own EAN-only pairs the build used, null when it grouped nothing
     */
    private record Build(StoreInventory inventory, Long globalVersion, VariantCodes storePairs) {
    }

    private Duration resolveTtl(Store storeEntity) {
        long minutes = Optional.ofNullable(storeEntity)
                .flatMap(Store::getInventoryCacheTtlMinutes)
                .filter(value -> value > 0)
                .map(Integer::longValue)
                .orElse(defaultTtlMinutes);
        return Duration.ofMinutes(Math.max(1, minutes));
    }

    private Build build(String storeId, Store storeEntity) {
        if (storeEntity == null) {
            return new Build(new StoreInventory(InventoryIndex.of(List.of()), LocalDateTime.now()), null, null);
        }

        // read once, before the feeds, so the feed parsing and the grouping see the same pairs
        VariantCodes storePairs = storeEanOnlyCodes.of(storeId);
        List<InventoryItem> ownItems = new ArrayList<>();
        Map<String, Double> sellRates = exchangeRates.getCurrentSellRates();
        for (StoreSupplierConnection connection : storeEntity.getOwnAndManualConnections()) {
            if (!connection.isEnabled()) {
                continue;
            }
            SupplierProviderDescriptor descriptor = descriptorFor(connection);
            if (descriptor != null) {
                ownItems.addAll(storeFeedItemLoader.load(storeId, connection.getSupplierName(), descriptor, sellRates, storePairs));
            }
        }

        // one reload of the global inventory for the whole build: its offers witness, its variant codes join the own
        // ones for linking own groups to global ones, and its EAN-only pairs hold here too, even where a store entry
        // shadows the flagged global one; the store's own pairs join them only in this store's pool and links
        GlobalMatchedInventory.Generation global = globalInventory.generation();
        VariantCodes eanOnly = global.eanOnlyCodes().plus(storePairs);
        InventoryAutoDiscovery.Grouping grouping = autoDiscovery.group(
                ownItems, pimCatalog.withStoreData(storeId), global::allItems, global.variantCodes(), eanOnly);
        InventoryIndex index = InventoryIndex.of(grouping.groups());
        MatchingRules rules = MatchingRules.of(matchingProperties);
        if (rules.linksOwnToGlobal() && !index.all().isEmpty()) {
            linkToGlobal(storeId, index, global, grouping.variantCodes(), rules.linkVetos());
        }
        return new Build(new StoreInventory(index, LocalDateTime.now()), global.version(), storePairs);
    }

    private static void linkToGlobal(String storeId, InventoryIndex index, GlobalMatchedInventory.Generation global,
                                     VariantCodes variantCodes, List<LinkVeto> vetos) {
        int linked = 0;
        Map<OwnToGlobalLinks.Outcome, Integer> leftOut = new EnumMap<>(OwnToGlobalLinks.Outcome.class);
        for (OwnToGlobalLinks.Decision decision : OwnToGlobalLinks.apply(index, global, variantCodes, vetos)) {
            if (decision.outcome() == OwnToGlobalLinks.Outcome.LINKED) {
                linked++;
            } else {
                leftOut.merge(decision.outcome(), 1, Integer::sum);
            }
        }
        log.info("Linked {} own groups of store {} to global groups by brand and code; left out: {}", linked, storeId, leftOut);
    }

    private SupplierProviderDescriptor descriptorFor(StoreSupplierConnection connection) {
        String identity = connection.getSupplierName();
        if (connection.getMode() == ConnectionMode.MANUAL) {
            return ManualSupplierDescriptor.forIdentity(identity);
        }
        return supplierProviderFactory.getDescriptor(identity);
    }
}
