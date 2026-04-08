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
public class RMAItemsRepository extends DynamoDbRepository<RMAItem> {

    public RMAItemsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public RMAItem findById(String rmaId, String rmaItemId) {
        return dynamoDBMapper.load(RMAItem.class, rmaId, rmaItemId);
    }

    public List<RMAItem> findByRmaId(String rmaId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":rmaId", new AttributeValue().withS(rmaId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("rmaId = :rmaId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMAItem.class, scanExpression);
    }

    public List<RMAItem> findBySerialNo(String serialNo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":serialNo", new AttributeValue().withS(serialNo));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("contains(serialNo, :serialNo)")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMAItem.class, scanExpression);
    }

    public List<RMAItem> findByDeliveryId(String deliveryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("deliveryId = :deliveryId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMAItem.class, scanExpression);
    }
}
