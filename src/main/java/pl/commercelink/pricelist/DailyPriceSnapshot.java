package pl.commercelink.pricelist;

import pl.commercelink.starter.csv.CSVReady;

import java.time.LocalDate;

class DailyPriceSnapshot implements CSVReady {

    static final String[] COLUMNS = {
            "pim_id",
            "lowest_price",
            "median_price",
            "highest_price",
            "price_spread",
            "lowest_available_qty",
            "total_available_qty",
            "lowest_price_distributor",
            "best_value_distributors_pipe_separated",
            "best_value_prices_pipe_separated",
            "all_available_distributors_pipe_separated",
            "snapshot_date"
    };

    private final String pimId;
    private final double lowestPrice;
    private final double medianPrice;
    private final double highestPrice;
    private final double priceSpread;
    private final long lowestAvailableQty;
    private final long totalAvailableQty;
    private final String lowestPriceDistributor;
    private final String bestValueDistributorsPipeSeparated;
    private final String bestValuePricesPipeSeparated;
    private final String allAvailableDistributorsPipeSeparated;
    private final LocalDate snapshotDate;

    DailyPriceSnapshot(
            String pimId,
            double lowestPrice,
            double medianPrice,
            double highestPrice,
            long lowestAvailableQty,
            long totalAvailableQty,
            String lowestPriceDistributor,
            String bestValueDistributorsPipeSeparated,
            String bestValuePricesPipeSeparated,
            String allAvailableDistributorsPipeSeparated,
            LocalDate snapshotDate
    ) {
        this.pimId = pimId;
        this.lowestPrice = lowestPrice;
        this.medianPrice = medianPrice;
        this.highestPrice = highestPrice;
        this.priceSpread = Math.round((highestPrice - lowestPrice) * 100.0) / 100.0;
        this.lowestAvailableQty = lowestAvailableQty;
        this.totalAvailableQty = totalAvailableQty;
        this.lowestPriceDistributor = lowestPriceDistributor;
        this.bestValueDistributorsPipeSeparated = bestValueDistributorsPipeSeparated;
        this.bestValuePricesPipeSeparated = bestValuePricesPipeSeparated;
        this.allAvailableDistributorsPipeSeparated = allAvailableDistributorsPipeSeparated;
        this.snapshotDate = snapshotDate;
    }

    /**
     * Factory method to create DailyPriceSnapshot from CSV row
     * Expected columns: pim_id, lowest_price, median_price, highest_price, price_spread,
     * lowest_available_qty, total_available_qty, lowest_price_distributor,
     * best_value_distributors_pipe_separated, best_value_prices_pipe_separated,
     * all_available_distributors_pipe_separated, snapshot_date
     */
    static DailyPriceSnapshot fromCsvRow(String[] csvRow) {
        return new DailyPriceSnapshot(
                csvRow[0],                              // pim_id
                Double.parseDouble(csvRow[1]),          // lowest_price
                Double.parseDouble(csvRow[2]),          // median_price
                Double.parseDouble(csvRow[3]),          // highest_price
                Long.parseLong(csvRow[5]),              // lowest_available_qty
                Long.parseLong(csvRow[6]),              // total_available_qty
                csvRow[7],                              // lowest_price_distributor
                csvRow[8],                              // best_value_distributors_pipe_separated
                csvRow[9],                              // best_value_prices_pipe_separated
                csvRow[10],                             // all_available_distributors_pipe_separated
                LocalDate.parse(csvRow[11])             // snapshot_date
        );
    }

    String getPimId() {
        return pimId;
    }

    double getLowestPrice() {
        return lowestPrice;
    }

    double getMedianPrice() {
        return medianPrice;
    }

    double getHighestPrice() {
        return highestPrice;
    }

    double getPriceSpread() {
        return priceSpread;
    }

    long getTotalAvailableQty() {
        return totalAvailableQty;
    }

    String getLowestPriceDistributor() {
        return lowestPriceDistributor;
    }

    LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    @Override
    public String[] asStringArray() {
        return new String[]{
                pimId,
                String.valueOf(lowestPrice),
                String.valueOf(medianPrice),
                String.valueOf(highestPrice),
                String.valueOf(priceSpread),
                String.valueOf(lowestAvailableQty),
                String.valueOf(totalAvailableQty),
                lowestPriceDistributor,
                bestValueDistributorsPipeSeparated,
                bestValuePricesPipeSeparated,
                allAvailableDistributorsPipeSeparated,
                snapshotDate.toString()
        };
    }
}