package pl.commercelink.pricelist;

import java.time.LocalDate;
import java.util.*;

public class SellingPriceHistory {

    private final String pimId;
    private final Map<LocalDate, Long> dailyLowestPrices;

    public SellingPriceHistory(String pimId) {
        this.pimId = pimId;
        this.dailyLowestPrices = new TreeMap<>();
    }

    SellingPriceHistory(String pimId, Map<LocalDate, Long> dailyLowestPrices) {
        this.pimId = pimId;
        this.dailyLowestPrices = new TreeMap<>(dailyLowestPrices);
    }

    public String getPimId() {
        return pimId;
    }

    Map<LocalDate, Long> getDailyLowestPrices() {
        return dailyLowestPrices;
    }

    public void recordPrice(LocalDate date, long price) {
        dailyLowestPrices.merge(date, price, Math::min);
    }

    /** A new history holding the lowest price of every day found in this or the other history; a null other is a copy. */
    public SellingPriceHistory mergedWith(SellingPriceHistory other) {
        SellingPriceHistory merged = new SellingPriceHistory(pimId, dailyLowestPrices);
        if (other != null) {
            other.dailyLowestPrices.forEach(merged::recordPrice);
        }
        return merged;
    }

    public void evictOlderThan(LocalDate cutoff) {
        dailyLowestPrices.entrySet().removeIf(e -> e.getKey().isBefore(cutoff));
    }

    public long getLowestPrice30d() {
        return dailyLowestPrices.values().stream().mapToLong(Long::longValue).min().orElse(0);
    }

    public long getLowestPriceOtherThan(long currentPrice) {
        return dailyLowestPrices.values().stream()
                .mapToLong(Long::longValue)
                .filter(p -> p != currentPrice)
                .min()
                .orElse(currentPrice);
    }

    String[] toCsvRow() {
        List<String> parts = new ArrayList<>();
        parts.add(pimId);
        dailyLowestPrices.forEach((date, price) -> parts.add(date + ":" + price));
        return parts.toArray(new String[0]);
    }

    static SellingPriceHistory fromCsvRow(String[] row) {
        Map<LocalDate, Long> prices = new TreeMap<>();
        for (int i = 1; i < row.length; i++) {
            if (row[i] == null || row[i].isEmpty()) continue;
            String[] parts = row[i].split(":");
            if (parts.length == 2) {
                try {
                    prices.put(LocalDate.parse(parts[0]), Long.parseLong(parts[1]));
                } catch (Exception e) {
                    // skip malformed entries
                }
            }
        }
        return new SellingPriceHistory(row[0], prices);
    }
}
