package pl.commercelink.notifications;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;

@DynamoDBTable(tableName = StoreNotificationRecord.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
public class StoreNotificationRecord {

    public static final String TABLE_NAME = "StoreNotifications";
    public static final String UNREAD_INDEX = "UnreadByStore";

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "notificationId")
    private String notificationId;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private StoreNotificationType type;
    @DynamoDBAttribute(attributeName = "severity")
    @DynamoDBTypeConvertedEnum
    private StoreNotificationSeverity severity;
    @DynamoDBAttribute(attributeName = "object")
    private String object;
    @DynamoDBAttribute(attributeName = "message")
    private String message;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = UNREAD_INDEX, attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
    @DynamoDBAttribute(attributeName = "readAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime readAt;
    // set only while unread, which keeps the UnreadByStore index down to unread notifications
    @DynamoDBIndexHashKey(globalSecondaryIndexName = UNREAD_INDEX, attributeName = "unreadStoreId")
    private String unreadStoreId;

    public static StoreNotificationRecord unread(String storeId, StoreNotification notification, LocalDateTime createdAt) {
        StoreNotificationRecord record = new StoreNotificationRecord();
        record.setStoreId(storeId);
        record.setNotificationId(StoreNotificationIds.of(notification.getType(), notification.getObject(),
                notification.getMessage()));
        record.setType(notification.getType());
        record.setSeverity(notification.getSeverity());
        record.setObject(notification.getObject());
        record.setMessage(notification.getMessage());
        record.setCreatedAt(createdAt);
        record.setUnreadStoreId(storeId);
        return record;
    }

    @DynamoDBIgnore
    public boolean isUnread() {
        return readAt == null;
    }
}
