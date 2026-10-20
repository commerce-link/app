package pl.commercelink.pricelist;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RollingPriceAggregateRepository {

    @Autowired
    private FileStorage fileStorage;

    @Value("${s3.bucket.datalake}")
    private String bucketName;

    @Value("${s3.bucket.stores}")
    private String storesBucketName;

    public Map<String, RollingPriceAggregate> loadAll() {
        return read(PriceDataLocation.global(bucketName));
    }

    /**
     * The global aggregate with the store's own laid over it product by product: a store that has no aggregate yet,
     * or no history of a product, is priced as before.
     */
    public Map<String, RollingPriceAggregate> loadForStore(String storeId) {
        Map<String, RollingPriceAggregate> aggregates = loadAll();
        try {
            aggregates.putAll(read(PriceDataLocation.store(storesBucketName, storeId)));
        } catch (RuntimeException e) {
            log.error("Rolling price aggregate of store {} is unreadable, the global one is used", storeId, e);
        }
        return aggregates;
    }

    private Map<String, RollingPriceAggregate> read(PriceDataLocation location) {
        Map<String, RollingPriceAggregate> aggregates = new HashMap<>();

        try {
            Pair<String, InputStreamReader> newest = fileStorage.findNewestByLastModified(location.bucket(), location.aggregateFolder());
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
