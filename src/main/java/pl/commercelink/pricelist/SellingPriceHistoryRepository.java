package pl.commercelink.pricelist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.storage.FileStorage;

import com.opencsv.CSVWriterBuilder;
import com.opencsv.ICSVWriter;

import java.io.*;
import java.util.*;

@Repository
public class SellingPriceHistoryRepository {

    private final FileStorage fileStorage;
    private final String bucketName;

    public SellingPriceHistoryRepository(FileStorage fileStorage, @Value("${s3.bucket.pricelists}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    public Map<String, SellingPriceHistory> load(String catalogId) {
        String key = getKey(catalogId);
        if (!fileStorage.canRead(bucketName, key)) {
            return new HashMap<>();
        }

        Map<String, SellingPriceHistory> histories = new HashMap<>();
        InputStreamReader reader = fileStorage.get(bucketName, key);
        CSVLoader csvLoader = new CSVLoader(reader);

        csvLoader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
            try {
                if (row.length >= 1 && row[0] != null && !row[0].isEmpty()) {
                    SellingPriceHistory history = SellingPriceHistory.fromCsvRow(row);
                    histories.put(history.getPimId(), history);
                }
            } catch (Exception e) {
                // skip invalid rows
            }
        });

        return histories;
    }

    public void save(String catalogId, Collection<SellingPriceHistory> histories) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ICSVWriter writer = new CSVWriterBuilder(new OutputStreamWriter(out))
                .withSeparator(CSVLoader.DEFAULT_SEPARATOR)
                .build()) {
            writer.writeNext(new String[]{"pim_id"});
            for (SellingPriceHistory history : histories) {
                writer.writeNext(history.toCsvRow());
            }
        }
        fileStorage.put(bucketName, getKey(catalogId), out.toByteArray());
    }

    private static String getKey(String catalogId) {
        return catalogId + "-price-history/history.csv";
    }
}
