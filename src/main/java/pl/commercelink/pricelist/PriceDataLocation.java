package pl.commercelink.pricelist;

import java.time.LocalDate;

/**
 * Where the daily price snapshots and rolling aggregates of one scope live: the datalake for the global ones,
 * {@code <storeId>/} in the stores bucket for a store's own, which the store deletion wipes with the rest of its files.
 */
record PriceDataLocation(String bucket, String prefix) {

    static final int ROLLING_WINDOW_DAYS = 30;

    private static final String SNAPSHOT_FOLDER = "daily-price-snapshot/";
    private static final String AGGREGATE_FOLDER = "rolling-price-aggregate/";

    static PriceDataLocation global(String datalakeBucket) {
        return new PriceDataLocation(datalakeBucket, "");
    }

    static PriceDataLocation store(String storesBucket, String storeId) {
        return new PriceDataLocation(storesBucket, storeId + "/");
    }

    String snapshotKey(LocalDate date) {
        return snapshotFolder() + date + ".csv";
    }

    String snapshotFolder() {
        return prefix + SNAPSHOT_FOLDER;
    }

    String aggregateKey(LocalDate date) {
        return aggregateFolder() + date + ".csv";
    }

    String aggregateFolder() {
        return prefix + AGGREGATE_FOLDER;
    }
}
