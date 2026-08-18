package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
class WarehouseDocumentItemRepository extends DynamoDbRepository<WarehouseDocumentItem> {

    WarehouseDocumentItemRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    void saveAll(List<WarehouseDocumentItem> items) {
        dynamoDBMapper.batchSave(items);
    }

    List<WarehouseDocumentItem> findByDocumentId(String documentId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":documentId", new AttributeValue().withS(documentId));

        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = new DynamoDBQueryExpression<WarehouseDocumentItem>()
                .withKeyConditionExpression("documentId = :documentId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.query(WarehouseDocumentItem.class, queryExpression);
    }

    boolean documentContainsProduct(String documentId, String ean, String mfn) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":documentId", new AttributeValue().withS(documentId));
        StringBuilder filterExpression = new StringBuilder();

        if (isNotBlank(ean)) {
            eav.put(":ean", new AttributeValue().withS(ean));
            appendFilter(filterExpression, "ean = :ean");
        }

        if (isNotBlank(mfn)) {
            eav.put(":mfn", new AttributeValue().withS(mfn));
            appendFilter(filterExpression, "mfn = :mfn");
        }

        DynamoDBQueryExpression<WarehouseDocumentItem> queryExpression = new DynamoDBQueryExpression<WarehouseDocumentItem>()
                .withKeyConditionExpression("documentId = :documentId")
                .withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(eav);

        return !dynamoDBMapper.query(WarehouseDocumentItem.class, queryExpression).isEmpty();
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
}
