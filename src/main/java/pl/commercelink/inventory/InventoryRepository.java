package pl.commercelink.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class InventoryRepository {

    private static final String FEED_MARKER = "-feed";
    // outside the feed names the reload reads, also for a version that does not know the hashes, so writing a hash
    // never makes an instance reload a feed
    private static final String HASH_PREFIX = "feed-hashes/";
    private static final String HASH_EXTENSION = ".sha256";

    private final FileStorage fileStorage;
    private final String bucketName;

    public InventoryRepository(FileStorage fileStorage, @Value("${s3.bucket.feeds}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    public void init() {
        // bucket already exists, no reason to init
    }

    public void store(String supplierName, byte[] data) {
        store(supplierName, data, "csv");
    }

    public void store(String supplierName, byte[] data, String fileExtension) {
        String key = createKey(supplierName, fileExtension);
        fileStorage.put(bucketName, key, data);
    }

    /**
     * The hash of the supplier's feed as it was last downloaded, with the time the feed file was stored. None when the
     * feed file is newer than the hash (put by hand, or by a version that keeps no hash), since the hash no longer
     * describes the stored file then.
     */
    public Optional<StoredHash> storedHash(String supplierName, String fileExtension) {
        String hashKey = hashKey(supplierName);
        Optional<Instant> hashedAt = fileStorage.findLastModified(bucketName, hashKey);
        if (hashedAt.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> storedAt = fileStorage.findLastModified(bucketName, createKey(supplierName, fileExtension));
        if (storedAt.isEmpty() || storedAt.get().isAfter(hashedAt.get())) {
            return Optional.empty();
        }
        String hash = new String(fileStorage.getBytes(bucketName, hashKey), StandardCharsets.UTF_8).trim();
        return Optional.of(new StoredHash(hash, storedAt.get()));
    }

    /** @param feedStoredAt when the feed file the hash describes was stored */
    public record StoredHash(String hash, Instant feedStoredAt) {
    }

    // Written again by a download that found the feed unchanged, which keeps the supplier's last download date moving.
    public void storeHash(String supplierName, String hash) {
        fileStorage.put(bucketName, hashKey(supplierName), hash.getBytes(StandardCharsets.UTF_8));
    }

    public boolean canRead(String supplierName) {
        String key = createKey(supplierName, "csv");
        return fileStorage.canRead(bucketName, key);
    }

    public Reader read(String supplierName) throws IOException {
        return read(supplierName, "csv");
    }

    public Reader read(String supplierName, String extension) throws IOException {
        String key = createKey(supplierName, extension);
        return fileStorage.get(bucketName, key);
    }

    private String createKey(String supplierName, String fileExtension) {
        return fileName(supplierName) + FEED_MARKER + "." + fileExtension;
    }

    private String hashKey(String supplierName) {
        return HASH_PREFIX + fileName(supplierName) + HASH_EXTENSION;
    }

    private static String fileName(String supplierName) {
        return supplierName.toLowerCase();
    }

    /** The date of each supplier's feed file, which tells the instances to reload the feed. */
    public Map<String, LocalDateTime> getLatestModifiedPerSupplier() {
        return feedsBySupplier(fileStorage.getAllObjectLastModified(bucketName, ""));
    }

    /**
     * When each supplier's feed was last downloaded: the later of its file and its hash, since a download that found
     * the feed unchanged writes the hash only.
     */
    public Map<String, LocalDateTime> getLastDownloadedPerSupplier() {
        Map<String, LocalDateTime> objects = fileStorage.getAllObjectLastModified(bucketName, "");
        Map<String, LocalDateTime> lastDownloaded = feedsBySupplier(objects);
        for (Map.Entry<String, LocalDateTime> entry : objects.entrySet()) {
            String key = entry.getKey();
            if (isHash(key)) {
                String supplierName = key.substring(HASH_PREFIX.length(), key.length() - HASH_EXTENSION.length());
                lastDownloaded.computeIfPresent(supplierName, (supplier, feed) -> later(feed, entry.getValue()));
            }
        }
        return lastDownloaded;
    }

    private static Map<String, LocalDateTime> feedsBySupplier(Map<String, LocalDateTime> objects) {
        Map<String, LocalDateTime> lastModifiedPerSupplier = new HashMap<>();
        for (Map.Entry<String, LocalDateTime> entry : objects.entrySet()) {
            String key = entry.getKey();
            if (!isHash(key) && key.contains(FEED_MARKER)) {
                String supplierName = key.substring(0, key.indexOf(FEED_MARKER));
                lastModifiedPerSupplier.put(supplierName, entry.getValue());
            }
        }
        return lastModifiedPerSupplier;
    }

    private static boolean isHash(String key) {
        return key.startsWith(HASH_PREFIX) && key.endsWith(HASH_EXTENSION);
    }

    private static LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }
}
