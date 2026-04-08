package pl.commercelink.pricelist;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.csv.CSVLoader;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Repository
public class RollingPriceAggregateRepository {

    @Autowired
    private FileStorage fileStorage;

    @Value("${s3.bucket.datalake}")
    private String bucketName;

    public Map<String, RollingPriceAggregate> loadAll() {
        Map<String, RollingPriceAggregate> aggregates = new HashMap<>();

        try {
            Pair<String, InputStreamReader> newest = fileStorage.findNewest(bucketName, "rolling-price-aggregate/");
            if (newest == null) {
                return aggregates;
            }

            CSVLoader csvLoader = new CSVLoader(newest.getRight());

            csvLoader.readRows(CSVLoader.DEFAULT_SEPARATOR, row -> {
                try {
                    if (row.length >= 19) {
                        RollingPriceAggregate aggregate = RollingPriceAggregate.fromCsvRow(row);
                        aggregates.put(aggregate.getPimId(), aggregate);
                    }
                } catch (Exception e) {
                    // Skip invalid rows silently
                }
            });

        } catch (Exception e) {
            // Exceptions will be logged to Sentry automatically
            throw new RuntimeException("Failed to load rolling price aggregates from CSV", e);
        }

        return aggregates;
    }
}
