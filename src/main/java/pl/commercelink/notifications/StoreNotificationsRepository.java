package pl.commercelink.notifications;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.Select;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static pl.commercelink.notifications.StoreNotificationRecord.TABLE_NAME;
import static pl.commercelink.notifications.StoreNotificationRecord.UNREAD_INDEX;

@Component
public class StoreNotificationsRepository extends DynamoDbRepository<StoreNotificationRecord> {

    private static final DynamoDbLocalDateTimeConverter DATE_TIME = new DynamoDbLocalDateTimeConverter();
    private static final String RECORD_EXISTS = "attribute_exists(notificationId)";

    public StoreNotificationsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public boolean putIfAbsent(StoreNotificationRecord record) {
        DynamoDBSaveExpression onlyIfAbsent = new DynamoDBSaveExpression()
                .withExpected(Map.of("notificationId", new ExpectedAttributeValue().withExists(false)));
        try {
            dynamoDBMapper.save(record, onlyIfAbsent);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public List<StoreNotificationRecord> findAll(String storeId) {
        List<StoreNotificationRecord> records = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        do {
            QueryResult result = amazonDynamoDB.query(new QueryRequest()
                    .withTableName(TABLE_NAME)
                    .withKeyConditionExpression("storeId = :storeId")
                    .withExpressionAttributeValues(Map.of(":storeId", new AttributeValue(storeId)))
                    .withConsistentRead(true)
                    .withExclusiveStartKey(startKey));
            result.getItems().forEach(item ->
                    records.add(dynamoDBMapper.marshallIntoObject(StoreNotificationRecord.class, item)));
            startKey = result.getLastEvaluatedKey();
        } while (hasMorePages(startKey));
        return records;
    }

    public Optional<StoreNotificationRecord> find(String storeId, String notificationId) {
        return Optional.ofNullable(dynamoDBMapper.load(StoreNotificationRecord.class, storeId, notificationId));
    }

    public long countUnread(String storeId) {
        long count = 0;
        Map<String, AttributeValue> startKey = null;
        do {
            QueryResult result = amazonDynamoDB.query(unreadQuery(storeId, startKey).withSelect(Select.COUNT));
            count += result.getCount() == null ? 0 : result.getCount();
            startKey = result.getLastEvaluatedKey();
        } while (hasMorePages(startKey));
        return count;
    }

    public List<String> findUnreadIds(String storeId) {
        List<String> ids = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        do {
            QueryResult result = amazonDynamoDB.query(unreadQuery(storeId, startKey));
            result.getItems().forEach(item -> ids.add(item.get("notificationId").getS()));
            startKey = result.getLastEvaluatedKey();
        } while (hasMorePages(startKey));
        return ids;
    }

    public boolean markRead(String storeId, String notificationId, LocalDateTime readAt) {
        return updateExisting(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key(storeId, notificationId))
                .withUpdateExpression("SET readAt = :readAt REMOVE unreadStoreId")
                .withConditionExpression(RECORD_EXISTS)
                .withExpressionAttributeValues(Map.of(":readAt", new AttributeValue(DATE_TIME.convert(readAt)))));
    }

    public boolean markUnread(String storeId, String notificationId) {
        return updateExisting(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key(storeId, notificationId))
                .withUpdateExpression("SET unreadStoreId = :storeId REMOVE readAt")
                .withConditionExpression(RECORD_EXISTS)
                .withExpressionAttributeValues(Map.of(":storeId", new AttributeValue(storeId))));
    }

    public void delete(String storeId, String notificationId) {
        amazonDynamoDB.deleteItem(new DeleteItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key(storeId, notificationId)));
    }

    private boolean updateExisting(UpdateItemRequest request) {
        try {
            amazonDynamoDB.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private static QueryRequest unreadQuery(String storeId, Map<String, AttributeValue> startKey) {
        return new QueryRequest()
                .withTableName(TABLE_NAME)
                .withIndexName(UNREAD_INDEX)
                .withKeyConditionExpression("unreadStoreId = :storeId")
                .withExpressionAttributeValues(Map.of(":storeId", new AttributeValue(storeId)))
                .withExclusiveStartKey(startKey);
    }

    private static Map<String, AttributeValue> key(String storeId, String notificationId) {
        return Map.of("storeId", new AttributeValue(storeId), "notificationId", new AttributeValue(notificationId));
    }

    private static boolean hasMorePages(Map<String, AttributeValue> lastEvaluatedKey) {
        return lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty();
    }
}
