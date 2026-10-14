package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.stores.StoreActivity;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class RollingPriceAggregateGenerator {

    static final String QUEUE = "supplier-rolling-price-aggregate-queue";

    private static final String CSV_SUFFIX = ".csv";

    private final FileStorage fileStorage;
    private final PriceDataFanOut fanOut;
    private final StoreActivity storeActivity;
    private final String datalakeBucket;
    private final String storesBucket;

    RollingPriceAggregateGenerator(FileStorage fileStorage,
                                   PriceDataFanOut fanOut,
                                   StoreActivity storeActivity,
                                   @Value("${s3.bucket.datalake}") String datalakeBucket,
                                   @Value("${s3.bucket.stores}") String storesBucket) {
        this.fileStorage = fileStorage;
        this.fanOut = fanOut;
        this.storeActivity = storeActivity;
        this.datalakeBucket = datalakeBucket;
        this.storesBucket = storesBucket;
    }

    @SqsListener(
            value = QUEUE,
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(String body) {
        PriceDataMessage message = PriceDataMessage.parse(body);
        LocalDate day = message.dayOrToday();

        if (message.isForStore()) {
            generateForStore(message.storeId(), day);
            return;
        }

        // Fan out first: a failing global aggregate must not leave the stores without theirs
        fanOut.toActiveStores(QUEUE, day);
        PriceDataLocation global = PriceDataLocation.global(datalakeBucket);
        writeAggregate(global, day, readLast30DailySnapshots(global, day));
    }

    private void generateForStore(String storeId, LocalDate day) {
        if (!storeActivity.isActive(storeId)) {
            log.warn("Rolling price aggregate skipped for store {}: the store is inactive", storeId);
            return;
        }
        PriceDataLocation location = PriceDataLocation.store(storesBucket, storeId);
        List<DailyPriceSnapshot> snapshots = readLast30DailySnapshots(location, day);
        if (snapshots.isEmpty()) {
            // A kept aggregate is the newest one and would override the global prices of the store forever
            fileStorage.deleteAll(location.bucket(), location.aggregateFolder());
            return;
        }
        writeAggregate(location, day, snapshots);
        deleteSnapshotsOutsideWindow(location, day);
    }

    private void writeAggregate(PriceDataLocation location, LocalDate day, List<DailyPriceSnapshot> allSnapshots) {
        Map<String, List<DailyPriceSnapshot>> snapshotsByProduct = allSnapshots.stream()
                .collect(Collectors.groupingBy(DailyPriceSnapshot::getPimId));

        List<RollingPriceAggregate> aggregates = new ArrayList<>();
        for (Map.Entry<String, List<DailyPriceSnapshot>> entry : snapshotsByProduct.entrySet()) {
            try {
                RollingPriceAggregateCalculator aggregator = new RollingPriceAggregateCalculator(entry.getKey(), entry.getValue());
                aggregates.add(aggregator.aggregate());
            } catch (Exception e) {
                // Skip products that fail to aggregate
            }
        }

        fileStorage.put(location.bucket(), location.aggregateKey(day), generateCsv(aggregates));
    }

    private List<DailyPriceSnapshot> readLast30DailySnapshots(PriceDataLocation location, LocalDate day) {
        List<DailyPriceSnapshot> allSnapshots = new ArrayList<>();

        for (int i = 0; i < PriceDataLocation.ROLLING_WINDOW_DAYS; i++) {
            String fileName = location.snapshotKey(day.minusDays(i));

            try {
                if (!fileStorage.canRead(location.bucket(), fileName)) {
                    continue;
                }

                InputStreamReader reader = fileStorage.get(location.bucket(), fileName);
                allSnapshots.addAll(parseDailySnapshotCsv(reader));

            } catch (Exception e) {
                // Skip files that fail to read
            }
        }

        return allSnapshots;
    }

    private List<DailyPriceSnapshot> parseDailySnapshotCsv(InputStreamReader reader) {
        List<DailyPriceSnapshot> snapshots = new ArrayList<>();
        CSVLoader csvLoader = new CSVLoader(reader);

        csvLoader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
            try {
                if (row.length >= 12) {
                    snapshots.add(DailyPriceSnapshot.fromCsvRow(row));
                }
            } catch (Exception e) {
                // Skip rows that fail to parse
            }
        });

        return snapshots;
    }

    // The bucket keeps every version, so a snapshot nobody reads any more is only cost; only a store's own are removed
    private void deleteSnapshotsOutsideWindow(PriceDataLocation location, LocalDate day) {
        LocalDate oldestKept = day.minusDays(PriceDataLocation.ROLLING_WINDOW_DAYS - 1L);
        String folder = location.snapshotFolder();
        try {
            for (String key : fileStorage.getAllObjectLastModified(location.bucket(), folder).keySet()) {
                LocalDate snapshotDay = dayOf(key, folder);
                if (snapshotDay != null && snapshotDay.isBefore(oldestKept)) {
                    fileStorage.delete(location.bucket(), key);
                }
            }
        } catch (Exception e) {
            log.error("Old daily price snapshots under {} were not removed", folder, e);
        }
    }

    private static LocalDate dayOf(String key, String folder) {
        if (!key.startsWith(folder) || !key.endsWith(CSV_SUFFIX)) {
            return null;
        }
        try {
            return LocalDate.parse(key.substring(folder.length(), key.length() - CSV_SUFFIX.length()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private byte[] generateCsv(List<RollingPriceAggregate> aggregates) {
        try {
            return new CSVWriter().writeAllRowsToBytes(aggregates, RollingPriceAggregate.COLUMNS);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }
}
