package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Repository
public class StoreFeedRepository {

    private static final String FEED_MARKER = "-feed.";

    private final FileStorage fileStorage;
    private final String bucketName;

    public StoreFeedRepository(FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        this.fileStorage = fileStorage;
        this.bucketName = bucketName;
    }

    public void store(String storeId, String supplierName, byte[] data, String fileExtension) {
        fileStorage.put(bucketName, key(storeId, supplierName, fileExtension), data);
    }

    public boolean canRead(String storeId, String supplierName, String fileExtension) {
        return fileStorage.canRead(bucketName, key(storeId, supplierName, fileExtension));
    }

    public Reader read(String storeId, String supplierName, String fileExtension) throws IOException {
        return fileStorage.get(bucketName, key(storeId, supplierName, fileExtension));
    }

    public void delete(String storeId, String supplierName) {
        fileStorage.deleteAll(bucketName, feedPrefix(storeId, supplierName));
    }

    public Map<String, LocalDateTime> feedLastModifiedByIdentity(String storeId) {
        String prefix = storeId + "/supplier-feeds/";
        Map<String, LocalDateTime> byIdentity = new HashMap<>();
        for (Map.Entry<String, LocalDateTime> entry : fileStorage.getAllObjectLastModified(bucketName, prefix).entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                continue;
            }
            String fileName = key.substring(prefix.length());
            int marker = fileName.lastIndexOf(FEED_MARKER);
            if (marker <= 0) {
                continue;
            }
            byIdentity.put(fileName.substring(0, marker), entry.getValue());
        }
        return byIdentity;
    }

    private String key(String storeId, String supplierName, String fileExtension) {
        return feedPrefix(storeId, supplierName) + fileExtension;
    }

    private String feedPrefix(String storeId, String supplierName) {
        return storeId + "/supplier-feeds/" + supplierName.toLowerCase(Locale.ROOT) + "-feed.";
    }
}
