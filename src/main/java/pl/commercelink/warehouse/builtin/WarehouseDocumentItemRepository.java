package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class WarehouseDocumentItemRepository extends DynamoDbRepository<WarehouseDocumentItem> {

    WarehouseDocumentItemRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    void saveAll(List<WarehouseDocumentItem> items) {
        dynamoDBMapper.batchSave(items);
    }

    List<WarehouseDocumentItem> findByDocumentNo(String documentNo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":documentNo", new AttributeValue().withS(documentNo));

        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = new DynamoDBQueryExpression<WarehouseDocumentItem>()
                .withKeyConditionExpression("documentNo = :documentNo")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(WarehouseDocumentItem.class, queryExpression);
    }

    List<WarehouseDocumentItem> findByDeliveryId(String deliveryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = new DynamoDBQueryExpression<WarehouseDocumentItem>()
                .withIndexName("DeliveryIdIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression("deliveryId = :deliveryId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(WarehouseDocumentItem.class, queryExpression);
    }

    List<WarehouseDocumentItem> findByMfn(String mfn) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":mfn", new AttributeValue().withS(mfn));

        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = new DynamoDBQueryExpression<WarehouseDocumentItem>()
                .withIndexName("MfnIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression("mfn = :mfn")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(WarehouseDocumentItem.class, queryExpression);
    }
}
