package pl.commercelink.pricelist;

import org.springframework.stereotype.Service;
import pl.commercelink.products.information.StorePimAliases;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class SellingPriceHistoryService {

    private final SellingPriceHistoryRepository sellingPriceHistoryRepository;

    private final StorePimAliases aliases;

    public SellingPriceHistoryService(SellingPriceHistoryRepository sellingPriceHistoryRepository, StorePimAliases aliases) {
        this.sellingPriceHistoryRepository = sellingPriceHistoryRepository;
        this.aliases = aliases;
    }

    public void update(String storeId, String catalogId, List<AvailabilityAndPrice> pricelist) {
        try {
            Map<String, SellingPriceHistory> histories = sellingPriceHistoryRepository.load(storeId, catalogId);
            LocalDate today = LocalDate.now();
            LocalDate cutoff = today.minusDays(30);

            for (AvailabilityAndPrice item : pricelist) {
                if (item.getPrice() <= 0) continue;
                SellingPriceHistory history = histories.computeIfAbsent(item.getPimId(), SellingPriceHistory::new);
                history.recordPrice(today, item.getPrice());
                // A clone's days are also kept under its source, so they survive deleting the clone.
                aliases.sourceOf(storeId, item.getPimId()).ifPresent(sourcePimId ->
                        histories.computeIfAbsent(sourcePimId, SellingPriceHistory::new).recordPrice(today, item.getPrice()));
            }

            for (SellingPriceHistory history : histories.values()) {
                history.evictOlderThan(cutoff);
            }
            histories.values().removeIf(h -> h.getLowestPrice30d() == 0);

            sellingPriceHistoryRepository.save(storeId, catalogId, histories.values());
        } catch (Exception e) {
            System.err.println("Failed to update selling price history for catalog " + catalogId + ": " + e.getMessage());
        }
    }
}
