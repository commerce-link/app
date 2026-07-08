package pl.commercelink.localdev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.csv.CSVReady;
import pl.commercelink.starter.csv.CSVWriter;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Component
@Profile("localdev")
public class LocalDevFeedSeeder {

    private static final String ACME_FEED_KEY = "acme-feed.csv";
    private static final String ACME_B_FEED_KEY = "acmeb-feed.csv";

    private final FileStorage fileStorage;
    private final String feedsBucket;

    public LocalDevFeedSeeder(FileStorage fileStorage,
                              @Value("${s3.bucket.feeds}") String feedsBucket) {
        this.fileStorage = fileStorage;
        this.feedsBucket = feedsBucket;
    }

    public void seedGlobalFeeds(List<CatalogSeedRow> rows) {
        put(feedsBucket, ACME_FEED_KEY, CatalogSeed.acmeFeed(rows), FeedRow.HEADERS);
        put(feedsBucket, ACME_B_FEED_KEY, CatalogSeed.acmeBFeed(rows), FeedRow.HEADERS);
    }

    private void put(String bucket, String key, List<? extends CSVReady> rows, String[] headers) {
        try {
            byte[] bytes = new CSVWriter().writeAllRowsToBytes(rows, headers);
            fileStorage.put(bucket, key, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write local seed CSV to s3://" + bucket + "/" + key, e);
        }
    }
}
