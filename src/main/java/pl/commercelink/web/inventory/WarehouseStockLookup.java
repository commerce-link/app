package pl.commercelink.web.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;

/** The store's own stock on hand, by EAN, from the same warehouse the prices page reads. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseStockLookup {

    // the warehouse query scans the whole table and the list refreshes on every filter, sort and page click
    private final Cache<String, Map<String, Long>> inStockByStore = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(1_000)
            .build();

    private final Warehouse warehouse;
    private final StoresRepository storesRepository;

    /** Quantity in stock (not in delivery) per requested EAN; empty when there is no own warehouse to ask. */
    public Map<String, Long> inStockByEan(@Nullable String storeId, Collection<String> eans) {
        if (storeId == null || eans.isEmpty()) {
            return Map.of();
        }
        Store store = storesRepository.findById(storeId);
        if (store == null || store.hasIntegration(IntegrationType.WMS_PROVIDER)) {
            return Map.of();
        }
        Map<String, Long> storeStock;
        try {
            storeStock = inStockByStore.get(storeId, this::loadInStock);
        } catch (RuntimeException e) {
            log.warn("Warehouse stock unavailable for the inventory browse page of store {}", storeId, e);
            return Map.of();
        }
        Map<String, Long> qty = new HashMap<>();
        for (String code : eans) {
            Long inStock = code == null ? null : storeStock.get(unifyEan(code));
            if (inStock != null) {
                qty.put(code, inStock);
            }
        }
        return qty;
    }

    private Map<String, Long> loadInStock(String storeId) {
        Map<String, Long> qty = new HashMap<>();
        for (WarehouseItemView item : warehouse.stockQueryService(storeId).searchAllAvailable(storeId)) {
            String ean = item.getEan();
            if (ean != null && !ean.isBlank() && !item.isInDelivery()) {
                qty.merge(unifyEan(ean), (long) item.getQty(), Long::sum);
            }
        }
        return Map.copyOf(qty);
    }
}
