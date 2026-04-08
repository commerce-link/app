package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.csv.CSVWriter;

import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

@Repository
public class MarketplaceOfferExportRepository {

    @Autowired
    private FileStorage fileStorage;

    @Value("${s3.bucket.stores}")
    private String bucketName;

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
        try {
            String key = buildS3Key(storeId, marketplace, catalogId);
            CSVWriter csvWriter = new CSVWriter();
            byte[] data = csvWriter.writeAllRowsToBytes(snapshots, MarketplaceOfferSnapshot.csvHeaders());
            fileStorage.put(bucketName, key, data);
        } catch (Exception e) {
            System.err.println("Failed to save marketplace export: " + e.getMessage());
        }
    }

    private String buildS3Key(String storeId, String marketplace, String catalogId) {
        return String.format("%s/marketplace-exports/%s/%s/latest.csv", storeId, marketplace, catalogId);
    }
}
