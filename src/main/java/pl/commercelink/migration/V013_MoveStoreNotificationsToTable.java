package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.notifications.StoreNotificationIds;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.executeUpdate;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.scanAndProcess;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "V013-move-store-notifications-to-table", order = "013", author = "commercelink")
public class V013_MoveStoreNotificationsToTable {

    private static final String TABLE_NAME = "StoreNotifications";
    private static final String UNREAD_INDEX = "UnreadByStore";
    private static final String STORES = "Stores";
    private static final String ACTIVE = "ACTIVE";
    private static final Duration ACTIVE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final DynamoDbLocalDateTimeConverter DATE_TIME = new DynamoDbLocalDateTimeConverter();

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void execute() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(TABLE_NAME)
                .withKeySchema(hashKey("storeId"), rangeKey("notificationId"))
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("notificationId"),
                        stringAttribute("unreadStoreId"), stringAttribute("createdAt"))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName(UNREAD_INDEX)
                        .withKeySchema(hashKey("unreadStoreId"), rangeKey("createdAt"))
                        .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY)))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
        waitUntilActive();
        LocalDateTime migratedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        scanAndProcess(dynamoDB, STORES, List.of("storeId", "notifications"),
                store -> moveNotifications(store, migratedAt));
    }

    void moveNotifications(Map<String, AttributeValue> store, LocalDateTime migratedAt) {
        AttributeValue storeId = store.get("storeId");
        AttributeValue notifications = store.get("notifications");
        if (storeId == null || notifications == null) {
            return;
        }
        List<AttributeValue> entries = notifications.getL() == null ? List.of() : notifications.getL();
        for (int position = 0; position < entries.size(); position++) {
            // the embedded list was appended to, so its first entry is the oldest and gets the earliest date
            LocalDateTime createdAt = migratedAt.minusSeconds(entries.size() - 1L - position);
            Map<String, AttributeValue> fields = entries.get(position).getM() == null ? Map.of() : entries.get(position).getM();
            putIfAbsent(recordItem(storeId.getS(), fields, createdAt));
        }
        executeUpdate(dynamoDB, STORES, Map.of("storeId", storeId), "REMOVE notifications", null, null);
    }

    static boolean isActive(TableDescription table) {
        boolean indexesActive = table.getGlobalSecondaryIndexes() == null || table.getGlobalSecondaryIndexes().stream()
                .allMatch(index -> ACTIVE.equals(index.getIndexStatus()));
        return ACTIVE.equals(table.getTableStatus()) && indexesActive;
    }

    private static Map<String, AttributeValue> recordItem(String storeId, Map<String, AttributeValue> fields,
                                                          LocalDateTime createdAt) {
        StoreNotificationType type = enumValue(StoreNotificationType.class, stringValue(fields.get("type")));
        StoreNotificationSeverity severity = enumValue(StoreNotificationSeverity.class, stringValue(fields.get("severity")));
        String object = stringValue(fields.get("object"));
        String message = stringValue(fields.get("message"));

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("storeId", new AttributeValue(storeId));
        item.put("notificationId", new AttributeValue(StoreNotificationIds.of(type, object, message)));
        item.put("createdAt", new AttributeValue(DATE_TIME.convert(createdAt)));
        item.put("unreadStoreId", new AttributeValue(storeId));
        if (type != null) {
            item.put("type", new AttributeValue(type.name()));
        }
        if (severity != null) {
            item.put("severity", new AttributeValue(severity.name()));
        }
        if (StringUtils.isNotBlank(object)) {
            item.put("object", new AttributeValue(object));
        }
        if (message != null) {
            item.put("message", new AttributeValue(message));
        }
        return item;
    }

    private void putIfAbsent(Map<String, AttributeValue> item) {
        try {
            dynamoDB.putItem(new PutItemRequest()
                    .withTableName(TABLE_NAME)
                    .withItem(item)
                    .withConditionExpression("attribute_not_exists(notificationId)"));
        } catch (ConditionalCheckFailedException e) {
            log.warn("Store notification {} is already in {}, skipping", item.get("notificationId").getS(), TABLE_NAME);
        }
    }

    private void waitUntilActive() {
        Instant deadline = Instant.now().plus(ACTIVE_TIMEOUT);
        while (!isActive(dynamoDB.describeTable(TABLE_NAME).getTable())) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("DynamoDB table " + TABLE_NAME + " did not become active within " + ACTIVE_TIMEOUT);
            }
            pause();
        }
    }

    private static void pause() {
        try {
            // DynamoDB rejects writes to a table whose index is still being created, so the migration has to wait
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for DynamoDB table " + TABLE_NAME, e);
        }
    }

    private static String stringValue(AttributeValue value) {
        return value == null ? null : value.getS();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
