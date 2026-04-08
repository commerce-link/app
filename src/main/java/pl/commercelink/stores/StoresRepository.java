package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.storage.FileStorage;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;
import pl.commercelink.starter.storage.FileImageStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StoresRepository extends DynamoDbRepository<Store> {

    private final FileImageStorage fileImageStorage;

    public StoresRepository(AmazonDynamoDB amazonDynamoDB, FileStorage fileStorage, @Value("${s3.bucket.stores}") String bucketName) {
        super(amazonDynamoDB);
        this.fileImageStorage = new FileImageStorage(fileStorage, bucketName);
    }

    public Store findById(String storeId) {
        return dynamoDBMapper.load(Store.class, storeId);
    }

    public List<Store> findAll() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        return dynamoDBMapper.scan(Store.class, scanExpression);
    }

    public String storeLogo(String storeId, String fileName, byte[] image) {
        // Remove current logo
        deleteLogo(storeId);

        String extension = fileImageStorage.getFileExtension(fileName);
        String location = storeId + "/logo." + extension;
        fileImageStorage.storeImage(location, image);
        return location;
    }

    public byte[] getLogoResponse(String storeId) {
        String location = findLogoLocationWithExtension(storeId);
        return fileImageStorage.getImage(location);
    }

    public MediaType getMediaType(String fileName) {
        return fileImageStorage.getMediaType(fileName);
    }

    private void deleteLogo(String storeId) {
        String location = findLogoLocationWithExtension(storeId);
        if (location == null) return;
        fileImageStorage.deleteImage(location);
    }

    public String findLogoLocationWithExtension(String storeId) {
        for (String extension : FileImageStorage.EXTENSIONS) {
            String location = storeId + "/logo." + extension;
            if (fileImageStorage.getImage(location) != null) {
                return location;
            }
        }
        return null;
    }

    @Cacheable(value = "storeByApiKey", key = "#apiKey")
    public String findByApiKey(String apiKey) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":apiKey", new AttributeValue().withS(apiKey));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("apiKey = :apiKey")
                .withExpressionAttributeValues(eav);

        List<Store> results = dynamoDBMapper.scan(Store.class, scanExpression);
        return results != null && !results.isEmpty() ? results.get(0).getStoreId() : null;
    }

}
