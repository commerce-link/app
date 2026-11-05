package pl.commercelink.marketplace.offermatching;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.util.Optional;

/**
 * The latest offer matching result of a store's marketplace, in the {@code stores} bucket next to the store's other
 * files (so a store's deletion takes it along). Only the latest run is kept: the links themselves are the lasting result.
 * A run still going on, or one that failed, is a second file next to it, removed when the run finishes.
 */
@Slf4j
@Component
public class OfferMatchResultStore {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final FileStorage fileStorage;
    private final String bucketName;

    public OfferMatchResultStore(FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    public void save(String storeId, String marketplace, OfferMatchResult result) {
        try {
            fileStorage.put(bucketName, key(storeId, marketplace), MAPPER.writeValueAsBytes(result));
        } catch (IOException e) {
            throw new IllegalStateException("Offer match result of store " + storeId + " cannot be written", e);
        }
    }

    public Optional<OfferMatchResult> latest(String storeId, String marketplace) {
        return read(key(storeId, marketplace), OfferMatchResult.class);
    }

    /** The run going on (or the one that failed), kept apart so the latest result stays on the page meanwhile. */
    public void saveProgress(String storeId, String marketplace, OfferMatchProgress progress) {
        try {
            fileStorage.put(bucketName, progressKey(storeId, marketplace), MAPPER.writeValueAsBytes(progress));
        } catch (IOException e) {
            throw new IllegalStateException("Offer match progress of store " + storeId + " cannot be written", e);
        }
    }

    public Optional<OfferMatchProgress> progress(String storeId, String marketplace) {
        return read(progressKey(storeId, marketplace), OfferMatchProgress.class);
    }

    public void deleteProgress(String storeId, String marketplace) {
        fileStorage.delete(bucketName, progressKey(storeId, marketplace));
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            if (!fileStorage.canRead(bucketName, key)) {
                return Optional.empty();
            }
            return Optional.of(MAPPER.readValue(fileStorage.getBytes(bucketName, key), type));
        } catch (Exception e) {
            // the page then offers a new run, which overwrites the file
            log.warn("Skipping unreadable offer match file {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private static String key(String storeId, String marketplace) {
        return String.format("%s/marketplace-offer-matches/%s/latest.json", storeId, marketplace);
    }

    private static String progressKey(String storeId, String marketplace) {
        return String.format("%s/marketplace-offer-matches/%s/in-progress.json", storeId, marketplace);
    }
}
