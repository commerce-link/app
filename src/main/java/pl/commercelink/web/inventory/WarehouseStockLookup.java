package pl.commercelink.web.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

/** The store's own stock on hand, by manufacturer code, from the same warehouse the prices page reads. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseStockLookup {

    // DynamoDB rejects an IN list longer than 100 values, and a page of 50 products can carry more codes than that.
    private static final int CODES_PER_QUERY = 100;

    private final Warehouse warehouse;
    private final StoresRepository storesRepository;

    /** Quantity in stock (not in delivery) per unified manufacturer code; empty when there is no own warehouse to ask. */
    public Map<String, Long> inStockByMfn(@Nullable String storeId, Collection<String> mfns) {
        if (storeId == null || mfns.isEmpty()) {
            return Map.of();
        }
        Store store = storesRepository.findById(storeId);
        if (store == null || store.hasIntegration(IntegrationType.WMS_PROVIDER)) {
            return Map.of();
        }
        try {
            StockQueryService stock = warehouse.stockQueryService(storeId);
            Map<String, Long> qty = new HashMap<>();
            List<String> codes = new ArrayList<>(mfns);
            for (int from = 0; from < codes.size(); from += CODES_PER_QUERY) {
                List<String> chunk = codes.subList(from, Math.min(from + CODES_PER_QUERY, codes.size()));
                for (WarehouseItemView item : stock.searchAllAvailableByMfns(storeId, chunk)) {
                    String mfn = unifyMfn(item.getMfn());
                    if (mfn != null && !item.isInDelivery()) {
                        qty.merge(mfn, (long) item.getQty(), Long::sum);
                    }
                }
            }
            return qty;
        } catch (RuntimeException e) {
            log.warn("Warehouse stock unavailable for the inventory browse page of store {}: {}", storeId, e.getMessage());
            return Map.of();
        }
    }
}
