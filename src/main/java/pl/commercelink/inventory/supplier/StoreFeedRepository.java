package pl.commercelink.inventory.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.storage.FileStorage;

import java.io.IOException;
import java.io.Reader;

@Repository
public class StoreFeedRepository {

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

    private String key(String storeId, String supplierName, String fileExtension) {
        return feedPrefix(storeId, supplierName) + fileExtension;
    }

    private String feedPrefix(String storeId, String supplierName) {
        return storeId + "/supplier-feeds/" + supplierName.toLowerCase() + "-feed.";
    }
}
