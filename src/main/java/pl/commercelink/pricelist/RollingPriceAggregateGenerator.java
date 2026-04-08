package pl.commercelink.pricelist;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "application.env", havingValue = "prod", matchIfMissing = false)
public class RollingPriceAggregateGenerator {

    @Autowired
    private FileStorage fileStorage;

    @Value("${s3.bucket.datalake}")
    private String bucketName;

    @SqsListener(
            value = "supplier-rolling-price-aggregate-queue",
            maxConcurrentMessages = "1",
            maxMessagesPerPoll = "1",
            pollTimeoutSeconds = "20"
    )
    public void handleMessage(String message) throws IOException {
        List<DailyPriceSnapshot> allSnapshots = readLast30DailySnapshots();

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

        byte[] csvBytes = generateCsv(aggregates);
        String fileName = "rolling-price-aggregate/" + LocalDate.now() + ".csv";
        fileStorage.put(bucketName, fileName, csvBytes);
    }

    private List<DailyPriceSnapshot> readLast30DailySnapshots() throws IOException {
        List<DailyPriceSnapshot> allSnapshots = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 30; i++) {
            LocalDate date = today.minusDays(i);
            String fileName = "daily-price-snapshot/" + date + ".csv";

            try {
                if (!fileStorage.canRead(bucketName, fileName)) {
                    continue;
                }

                InputStreamReader reader = fileStorage.get(bucketName, fileName);
                List<DailyPriceSnapshot> snapshots = parseDailySnapshotCsv(reader);
                allSnapshots.addAll(snapshots);

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

    private byte[] generateCsv(List<RollingPriceAggregate> aggregates) {
        try {
            return new CSVWriter().writeAllRowsToBytes(aggregates, RollingPriceAggregate.COLUMNS);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }
}
