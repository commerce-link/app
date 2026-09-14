package pl.commercelink.web.inventory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import pl.commercelink.warehouse.api.StockSummary;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.Duration;

@Component
public class WarehouseSummaryService {

    // counting the built-in warehouse loads every available item of the store, so a page refresh must not repeat it
    private final Cache<String, StockSummary> summaries = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    private final Warehouse warehouse;

    public WarehouseSummaryService(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public StockSummary summaryFor(String storeId) {
        return summaries.get(storeId, id -> warehouse.stockQueryService(id).summarizeAvailable(id));
    }
}
