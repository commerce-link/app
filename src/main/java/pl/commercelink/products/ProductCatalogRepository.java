package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductCatalogRepository extends DynamoDbRepository<ProductCatalog> {

    public ProductCatalogRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public ProductCatalog findById(String storeId, String catalogId) {
        return dynamoDBMapper.load(ProductCatalog.class, storeId, catalogId);
    }

    public List<ProductCatalog> findAll(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(ProductCatalog.class, scanExpression);
    }
}
