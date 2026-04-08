package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RMACentersRepository extends DynamoDbRepository<RMACenter> {

    public RMACentersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public RMACenter findById(String storeId, String rmaCenterId) {
        return dynamoDBMapper.load(RMACenter.class, storeId, rmaCenterId);
    }

    public List<RMACenter> findByProviderName(String storeId, String providerName) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":storeIdDefault", new AttributeValue().withS("default"));
        eav.put(":provider", new AttributeValue().withS(providerName));

        DynamoDBScanExpression scan = new DynamoDBScanExpression()
                .withFilterExpression("(storeId = :storeId or storeId = :storeIdDefault) and provider = :provider")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMACenter.class, scan);
    }

    public List<RMACenter> findByStoreId(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":storeIdDefault", new AttributeValue().withS("default"));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId or storeId = :storeIdDefault")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMACenter.class, scanExpression);
    }

}
