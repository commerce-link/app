package pl.commercelink.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Repository
public class InventoryRepository {

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
        return supplierName.toLowerCase() + "-feed." + fileExtension;
    }

    public Map<String, LocalDateTime> getLatestModifiedPerSupplier() {
        Map<String, LocalDateTime> lastModifiedMap = fileStorage.getAllObjectLastModified(bucketName, "");
        Map<String, LocalDateTime> lastModifiedPerSupplier = new HashMap<>();

        for (Map.Entry<String, LocalDateTime> entry : lastModifiedMap.entrySet()) {
            String key = entry.getKey();
            if (key.contains("-feed")) {
                String supplierName = key.substring(0, key.indexOf("-feed"));
                lastModifiedPerSupplier.put(supplierName, entry.getValue());
            }
        }

        return lastModifiedPerSupplier;
    }
}
