package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.InputStreamReader;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

@Repository
public class MarketplaceOfferExportRepository {

    private static final DateTimeFormatter RUN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC);

    private final FileStorage fileStorage;
    private final String bucketName;
    private final Clock clock;

    @Autowired
    public MarketplaceOfferExportRepository(FileStorage fileStorage,
                                            @Value("${s3.bucket.stores}") String bucketName) {
        this(fileStorage, bucketName, Clock.systemUTC());
    }

    MarketplaceOfferExportRepository(FileStorage fileStorage, String bucketName, Clock clock) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
        this.clock = clock;
    }

    public List<MarketplaceOfferSnapshot> loadPreviousExport(String storeId, String catalogId, String marketplace) {
        try {
            String key = buildS3Key(storeId, marketplace, catalogId);
            if (!fileStorage.canRead(bucketName, key)) {
                return new LinkedList<>();
            }
            InputStreamReader reader = fileStorage.get(bucketName, key);
            CSVLoader csvLoader = new CSVLoader(reader);
            Pair<String[], List<String[]>> data = csvLoader.readHeadersAndRows(CSVLoader.DEFAULT_SEPARATOR);

            List<MarketplaceOfferSnapshot> snapshots = new LinkedList<>();
            for (String[] row : data.getSecond()) {
                snapshots.add(MarketplaceOfferSnapshot.fromStringArray(row));
            }
            return snapshots;
        } catch (Exception e) {
            System.err.println("Failed to load previous marketplace export: " + e.getMessage());
            return new LinkedList<>();
        }
    }

    public void saveCurrentExport(String storeId, String catalogId, String marketplace, List<MarketplaceOfferSnapshot> snapshots) {
        byte[] data;
        try {
            CSVWriter csvWriter = new CSVWriter();
            data = csvWriter.writeAllRowsToBytes(snapshots, MarketplaceOfferSnapshot.csvHeaders());
            fileStorage.put(bucketName, buildS3Key(storeId, marketplace, catalogId), data);
        } catch (Exception e) {
            System.err.println("Failed to save marketplace export: " + e.getMessage());
            return;
        }

        try {
            fileStorage.put(bucketName, buildRunHistoryS3Key(storeId, marketplace, catalogId), data);
        } catch (Exception e) {
            System.err.println("Failed to archive marketplace export: " + e.getMessage());
        }
    }

    private String buildS3Key(String storeId, String marketplace, String catalogId) {
        return String.format("%s/marketplace-exports/%s/%s/latest.csv", storeId, marketplace, catalogId);
    }

    private String buildRunHistoryS3Key(String storeId, String marketplace, String catalogId) {
        return String.format("marketplace-export-runs/%s/%s/%s/%s.csv",
                storeId, marketplace, catalogId, RUN_TIMESTAMP.format(clock.instant()));
    }
}
