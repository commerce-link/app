package pl.commercelink.pricelist;

import pl.commercelink.starter.csv.CSVReady;

import java.time.LocalDate;

public class RollingPriceAggregate implements CSVReady {

    static final String[] COLUMNS = {
            "pim_id",
            "median_lowest_price_30d",
            "median_median_price_30d",
            "median_highest_price_30d",
            "min_lowest_price_30d",
            "max_lowest_price_30d",
            "current_lowest_price",
            "price_vs_30d_min_pct",
            "price_vs_30d_median_pct",
            "days_below_median",
            "days_at_min_price",
            "best_distributors_30d",
            "best_distributor_frequency",
            "total_snapshots",
            "median_price_spread_30d",
            "price_volatility_score",
            "median_total_qty_30d",
            "last_snapshot_date",
            "first_snapshot_date"
    };

    private final String pimId; // Product identifier
    private final double medianLowestPrice30d; // Median of lowest prices over 30 days
    private final double medianMedianPrice30d; // Median of median prices over 30 days
    private final double medianHighestPrice30d; // Median of highest prices over 30 days
    private final double minLowestPrice30d; // Absolute minimum price seen in 30 days (for promotion detection)
    private final double maxLowestPrice30d; // Absolute maximum price seen in 30 days
    private final double currentLowestPrice; // Most recent lowest price from latest snapshot
    private final double priceVs30dMinPct; // Current price vs 30-day minimum percentage (e.g., -5% = 5% below min)
    private final double priceVs30dMedianPct; // Current price vs 30-day median percentage (e.g., -10% = good deal)
    private final int daysBelowMedian; // Number of days price was below median (frequency of good prices)
    private final int daysAtMinPrice; // Number of days at minimum price (promotion frequency)
    private final String bestDistributors30d; // Pipe-separated list of distributors who had lowest price most frequently
    private final String bestDistributorFrequency; // Pipe-separated counts showing how many days each was best (e.g., "Also:12|Morele:8")
    private final int totalSnapshots; // Number of daily snapshots included (should be ~30)
    private final double medianPriceSpread30d; // Median spread (highest - lowest) to gauge market competition
    private final double priceVolatilityScore; // Standard deviation / median price (0-1 scale, higher = more volatile)
    private final long medianTotalQty30d; // Median total quantity available across all distributors
    private final LocalDate lastSnapshotDate; // Most recent snapshot date
    private final LocalDate firstSnapshotDate; // Oldest snapshot date in aggregate

    public RollingPriceAggregate(
            String pimId,
            double medianLowestPrice30d,
            double medianMedianPrice30d,
            double medianHighestPrice30d,
            double minLowestPrice30d,
            double maxLowestPrice30d,
            double currentLowestPrice,
            double priceVs30dMinPct,
            double priceVs30dMedianPct,
            int daysBelowMedian,
            int daysAtMinPrice,
            String bestDistributors30d,
            String bestDistributorFrequency,
            int totalSnapshots,
            double medianPriceSpread30d,
            double priceVolatilityScore,
            long medianTotalQty30d,
            LocalDate lastSnapshotDate,
            LocalDate firstSnapshotDate
    ) {
        this.pimId = pimId;
        this.medianLowestPrice30d = medianLowestPrice30d;
        this.medianMedianPrice30d = medianMedianPrice30d;
        this.medianHighestPrice30d = medianHighestPrice30d;
        this.minLowestPrice30d = minLowestPrice30d;
        this.maxLowestPrice30d = maxLowestPrice30d;
        this.currentLowestPrice = currentLowestPrice;
        this.priceVs30dMinPct = priceVs30dMinPct;
        this.priceVs30dMedianPct = priceVs30dMedianPct;
        this.daysBelowMedian = daysBelowMedian;
        this.daysAtMinPrice = daysAtMinPrice;
        this.bestDistributors30d = bestDistributors30d;
        this.bestDistributorFrequency = bestDistributorFrequency;
        this.totalSnapshots = totalSnapshots;
        this.medianPriceSpread30d = medianPriceSpread30d;
        this.priceVolatilityScore = priceVolatilityScore;
        this.medianTotalQty30d = medianTotalQty30d;
        this.lastSnapshotDate = lastSnapshotDate;
        this.firstSnapshotDate = firstSnapshotDate;
    }

    public String getPimId() {
        return pimId;
    }

    public double getMedianLowestPrice30d() {
        return medianLowestPrice30d;
    }

    public double getMedianMedianPrice30d() {
        return medianMedianPrice30d;
    }

    public double getCurrentLowestPrice() {
        return currentLowestPrice;
    }

    public double getMinLowestPrice30d() {
        return minLowestPrice30d;
    }

    public boolean isAtLowestPrice() {
        return currentLowestPrice > 0 && currentLowestPrice <= minLowestPrice30d;
    }

    public boolean isHotDeal() {
        double threshold = Math.max(0.05, 2 * priceVolatilityScore);
        return currentLowestPrice > 0 && medianLowestPrice30d > 0
                && currentLowestPrice <= medianLowestPrice30d * (1 - threshold);
    }

    public static RollingPriceAggregate fromCsvRow(String[] row) {
        if (row.length < 19) {
            throw new IllegalArgumentException("CSV row must have at least 19 columns");
        }

        return new RollingPriceAggregate(
                row[0], // pimId
                parseDouble(row[1]), // medianLowestPrice30d
                parseDouble(row[2]), // medianMedianPrice30d
                parseDouble(row[3]), // medianHighestPrice30d
                parseDouble(row[4]), // minLowestPrice30d
                parseDouble(row[5]), // maxLowestPrice30d
                parseDouble(row[6]), // currentLowestPrice
                parseDouble(row[7]), // priceVs30dMinPct
                parseDouble(row[8]), // priceVs30dMedianPct
                parseInt(row[9]), // daysBelowMedian
                parseInt(row[10]), // daysAtMinPrice
                row[11], // bestDistributors30d
                row[12], // bestDistributorFrequency
                parseInt(row[13]), // totalSnapshots
                parseDouble(row[14]), // medianPriceSpread30d
                parseDouble(row[15]), // priceVolatilityScore
                parseLong(row[16]), // medianTotalQty30d
                LocalDate.parse(row[17]), // lastSnapshotDate
                LocalDate.parse(row[18]) // firstSnapshotDate
        );
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                pimId,
                String.valueOf(medianLowestPrice30d),
                String.valueOf(medianMedianPrice30d),
                String.valueOf(medianHighestPrice30d),
                String.valueOf(minLowestPrice30d),
                String.valueOf(maxLowestPrice30d),
                String.valueOf(currentLowestPrice),
                String.format("%.2f", priceVs30dMinPct),
                String.format("%.2f", priceVs30dMedianPct),
                String.valueOf(daysBelowMedian),
                String.valueOf(daysAtMinPrice),
                bestDistributors30d,
                bestDistributorFrequency,
                String.valueOf(totalSnapshots),
                String.valueOf(medianPriceSpread30d),
                String.format("%.3f", priceVolatilityScore),
                String.valueOf(medianTotalQty30d),
                lastSnapshotDate.toString(),
                firstSnapshotDate.toString()
        };
    }
}
