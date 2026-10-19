package pl.commercelink.notifications;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.junit.jupiter.api.Test;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StoreNotificationRecordTest {

    @Test
    void startsANewNotificationUnreadAndInTheUnreadIndexOfItsStore() {
        // given
        StoreNotification notification = new StoreNotification(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, "rma-7", "Allegro refunded the buyer for return REF/1");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 14, 15, 32);

        // when
        StoreNotificationRecord record = StoreNotificationRecord.unread("store-1", notification, createdAt);

        // then
        assertThat(record.getStoreId()).isEqualTo("store-1");
        assertThat(record.getNotificationId()).isEqualTo("MARKETPLACE_RETURN_REFUNDED:rma-7");
        assertThat(record.getType()).isEqualTo(StoreNotificationType.MARKETPLACE_RETURN_REFUNDED);
        assertThat(record.getSeverity()).isEqualTo(StoreNotificationSeverity.WARNING);
        assertThat(record.getObject()).isEqualTo("rma-7");
        assertThat(record.getMessage()).isEqualTo("Allegro refunded the buyer for return REF/1");
        assertThat(record.getCreatedAt()).isEqualTo(createdAt);
        assertThat(record.getReadAt()).isNull();
        assertThat(record.getUnreadStoreId()).isEqualTo("store-1");
        assertThat(record.isUnread()).isTrue();
    }

    @Test
    void isReadOnceItHasAReadDate() {
        // given
        StoreNotificationRecord record = new StoreNotificationRecord();

        // when
        record.setReadAt(LocalDateTime.of(2026, 9, 14, 16, 0));

        // then
        assertThat(record.isUnread()).isFalse();
    }

    @Test
    void aTypeWrittenByANewerVersionIsReadAsUnknownInsteadOfFailingTheWholeList() {
        // given
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        Map<String, AttributeValue> item = Map.of("storeId", new AttributeValue("store-1"),
                "notificationId", new AttributeValue("TYPE_OF_A_NEWER_VERSION:x"),
                "type", new AttributeValue("TYPE_OF_A_NEWER_VERSION"), "severity", new AttributeValue("WARNING"),
                "message", new AttributeValue("written by a newer version"));

        // when
        StoreNotificationRecord record = mapper.marshallIntoObject(StoreNotificationRecord.class, item);

        // then
        assertThat(record.getType()).isNull();
        assertThat(record.getSeverity()).isEqualTo(StoreNotificationSeverity.WARNING);
        assertThat(record.getMessage()).isEqualTo("written by a newer version");
    }

    @Test
    void aKnownTypeIsStoredAndReadByItsName() {
        // given
        DynamoDBMapper mapper = new DynamoDBMapper(mock(AmazonDynamoDB.class));
        StoreNotificationRecord record = StoreNotificationRecord.unread("store-1", new StoreNotification(
                StoreNotificationSeverity.WARNING, StoreNotificationType.DELIVERY_REQUEST_REJECTED, "d-1", "m"),
                LocalDateTime.of(2026, 10, 3, 12, 0));

        // when
        Map<String, AttributeValue> item = mapper.getTableModel(StoreNotificationRecord.class).convert(record);

        // then
        assertThat(item.get("type").getS()).isEqualTo("DELIVERY_REQUEST_REJECTED");
        assertThat(mapper.marshallIntoObject(StoreNotificationRecord.class, item).getType())
                .isEqualTo(StoreNotificationType.DELIVERY_REQUEST_REJECTED);
    }
}
