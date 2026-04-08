package pl.commercelink.pricelist;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class SellingPriceHistoryService {

    private final SellingPriceHistoryRepository sellingPriceHistoryRepository;

    public SellingPriceHistoryService(SellingPriceHistoryRepository sellingPriceHistoryRepository) {
        this.sellingPriceHistoryRepository = sellingPriceHistoryRepository;
    }

    public void update(String catalogId, List<AvailabilityAndPrice> pricelist) {
        try {
            Map<String, SellingPriceHistory> histories = sellingPriceHistoryRepository.load(catalogId);
            LocalDate today = LocalDate.now();
            LocalDate cutoff = today.minusDays(30);

            for (AvailabilityAndPrice item : pricelist) {
                if (item.getPrice() <= 0) continue;
                SellingPriceHistory history = histories.computeIfAbsent(item.getPimId(), SellingPriceHistory::new);
                history.recordPrice(today, item.getPrice());
            }

            for (SellingPriceHistory history : histories.values()) {
                history.evictOlderThan(cutoff);
            }
            histories.values().removeIf(h -> h.getLowestPrice30d() == 0);

            sellingPriceHistoryRepository.save(catalogId, histories.values());
        } catch (Exception e) {
            System.err.println("Failed to update selling price history for catalog " + catalogId + ": " + e.getMessage());
        }
    }
}
