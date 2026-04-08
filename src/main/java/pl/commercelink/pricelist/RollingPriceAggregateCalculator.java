package pl.commercelink.pricelist;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates daily price snapshots for a single product over 30 days
 */
class RollingPriceAggregateCalculator {
    private final String pimId;
    private final List<DailyPriceSnapshot> snapshots;

    RollingPriceAggregateCalculator(String pimId, List<DailyPriceSnapshot> snapshots) {
        this.pimId = pimId;
        this.snapshots = snapshots;
    }

    RollingPriceAggregate aggregate() {
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("Cannot aggregate empty snapshots for product: " + pimId);
        }

        // Sort snapshots by date
        snapshots.sort(Comparator.comparing(DailyPriceSnapshot::getSnapshotDate));

        // Core price medians
        double medianLowestPrice30d = calculateMedian(snapshots.stream()
                .map(DailyPriceSnapshot::getLowestPrice)
                .collect(Collectors.toList()));
        double medianMedianPrice30d = calculateMedian(snapshots.stream()
                .map(DailyPriceSnapshot::getMedianPrice)
                .collect(Collectors.toList()));
        double medianHighestPrice30d = calculateMedian(snapshots.stream()
                .map(DailyPriceSnapshot::getHighestPrice)
                .collect(Collectors.toList()));

        // Price range analysis
        double minLowestPrice30d = snapshots.stream()
                .mapToDouble(DailyPriceSnapshot::getLowestPrice)
                .min()
                .orElse(0.0);
        double maxLowestPrice30d = snapshots.stream()
                .mapToDouble(DailyPriceSnapshot::getLowestPrice)
                .max()
                .orElse(0.0);
        double currentLowestPrice = snapshots.get(snapshots.size() - 1).getLowestPrice();

        // Calculate percentage differences
        double priceVs30dMinPct = calculatePercentageDifference(currentLowestPrice, minLowestPrice30d);
        double priceVs30dMedianPct = calculatePercentageDifference(currentLowestPrice, medianLowestPrice30d);

        // Purchasing signals
        int daysBelowMedian = (int) snapshots.stream()
                .filter(s -> s.getLowestPrice() < medianLowestPrice30d)
                .count();
        int daysAtMinPrice = (int) snapshots.stream()
                .filter(s -> Math.abs(s.getLowestPrice() - minLowestPrice30d) < 0.01) // tolerance for double comparison
                .count();

        // Price volatility (standard deviation / median)
        double priceVolatilityScore = calculateVolatilityScore(
                snapshots.stream().map(DailyPriceSnapshot::getLowestPrice).collect(Collectors.toList()),
                medianLowestPrice30d
        );

        // Distributor intelligence
        Map<String, Long> distributorFrequency = snapshots.stream()
                .collect(Collectors.groupingBy(
                        DailyPriceSnapshot::getLowestPriceDistributor,
                        Collectors.counting()
                ));

        // Sort distributors by frequency (descending)
        List<Map.Entry<String, Long>> sortedDistributors = distributorFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());

        String bestDistributors30d = sortedDistributors.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.joining("|"));

        String bestDistributorFrequency = sortedDistributors.stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("|"));

        // Metadata
        int totalSnapshots = snapshots.size();
        double medianPriceSpread30d = calculateMedian(snapshots.stream()
                .map(DailyPriceSnapshot::getPriceSpread)
                .collect(Collectors.toList()));
        long medianTotalQty30d = (long) calculateMedian(snapshots.stream()
                .map(s -> (double) s.getTotalAvailableQty())
                .collect(Collectors.toList()));
        LocalDate lastSnapshotDate = snapshots.get(snapshots.size() - 1).getSnapshotDate();
        LocalDate firstSnapshotDate = snapshots.get(0).getSnapshotDate();

        return new RollingPriceAggregate(
                pimId,
                round(medianLowestPrice30d),
                round(medianMedianPrice30d),
                round(medianHighestPrice30d),
                round(minLowestPrice30d),
                round(maxLowestPrice30d),
                round(currentLowestPrice),
                priceVs30dMinPct,
                priceVs30dMedianPct,
                daysBelowMedian,
                daysAtMinPrice,
                bestDistributors30d,
                bestDistributorFrequency,
                totalSnapshots,
                round(medianPriceSpread30d),
                priceVolatilityScore,
                medianTotalQty30d,
                lastSnapshotDate,
                firstSnapshotDate
        );
    }

    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    private double calculatePercentageDifference(double current, double reference) {
        if (reference == 0) {
            return 0.0;
        }
        return ((current - reference) / reference) * 100.0;
    }

    private double calculateVolatilityScore(List<Double> prices, double median) {
        if (prices.isEmpty() || median == 0) {
            return 0.0;
        }

        // Calculate standard deviation
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = prices.stream()
                .mapToDouble(price -> Math.pow(price - mean, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        // Volatility score = standard deviation / median
        return standardDeviation / median;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
