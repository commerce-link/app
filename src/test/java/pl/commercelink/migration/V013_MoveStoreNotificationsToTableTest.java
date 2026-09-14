package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V013_MoveStoreNotificationsToTableTest {

    private static final LocalDateTime MIGRATED_AT = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Mock
    private AmazonDynamoDB dynamoDB;
    private V013_MoveStoreNotificationsToTable migration;

    @BeforeEach
    void setUp() {
        // no-op pause so the wait-for-ACTIVE loop never sleeps for real in tests
        migration = new V013_MoveStoreNotificationsToTable(dynamoDB, millis -> { });
    }

    // advances on every clock read so a deadline test can pass it without any real waiting
    private static final class AdvancingClock extends Clock {
        private Instant instant;
        private final Duration step;

        AdvancingClock(Instant start, Duration step) {
            this.instant = start;
            this.step = step;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant current = instant;
            instant = instant.plus(step);
            return current;
        }
    }

    private static AttributeValue string(String value) {
        return new AttributeValue().withS(value);
    }

    private static AttributeValue notification(String severity, String type, String object, String message) {
        Map<String, AttributeValue> fields = new HashMap<>();
        fields.put("severity", string(severity));
        fields.put("type", string(type));
        if (object != null) {
            fields.put("object", string(object));
        }
        fields.put("message", string(message));
        return new AttributeValue().withM(fields);
    }

    private static Map<String, AttributeValue> store(AttributeValue... notifications) {
        return Map.of("storeId", string("store-1"), "notifications", new AttributeValue().withL(notifications));
    }

    private static TableDescription table(String tableStatus, String indexStatus) {
        return new TableDescription()
                .withTableStatus(tableStatus)
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndexDescription()
                        .withIndexName("UnreadByStore")
                        .withIndexStatus(indexStatus));
    }

    @Test
    void createsTheNotificationsTableWithTheSparseUnreadIndexAndMovesEveryStore() {
        // given
        when(dynamoDB.describeTable("StoreNotifications"))
                .thenReturn(new DescribeTableResult().withTable(table("ACTIVE", "ACTIVE")));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of(
                store(notification("WARNING", "UNAUTHENTICATED", "allegro_marketplace", "Expired")))));
        ArgumentCaptor<CreateTableRequest> created = ArgumentCaptor.forClass(CreateTableRequest.class);
        ArgumentCaptor<ScanRequest> scanned = ArgumentCaptor.forClass(ScanRequest.class);

        // when
        migration.execute();

        // then
        verify(dynamoDB).createTable(created.capture());
        CreateTableRequest request = created.getValue();
        assertThat(request.getTableName()).isEqualTo("StoreNotifications");
        assertThat(request.getBillingMode()).isEqualTo("PAY_PER_REQUEST");
        assertThat(request.getKeySchema()).extracting(KeySchemaElement::getAttributeName, KeySchemaElement::getKeyType)
                .containsExactly(tuple("storeId", "HASH"), tuple("notificationId", "RANGE"));
        assertThat(request.getAttributeDefinitions()).extracting(AttributeDefinition::getAttributeName)
                .containsExactlyInAnyOrder("storeId", "notificationId", "unreadStoreId", "createdAt");
        GlobalSecondaryIndex index = request.getGlobalSecondaryIndexes().get(0);
        assertThat(index.getIndexName()).isEqualTo("UnreadByStore");
        assertThat(index.getKeySchema()).extracting(KeySchemaElement::getAttributeName, KeySchemaElement::getKeyType)
                .containsExactly(tuple("unreadStoreId", "HASH"), tuple("createdAt", "RANGE"));
        assertThat(index.getProjection().getProjectionType()).isEqualTo("KEYS_ONLY");
        verify(dynamoDB).scan(scanned.capture());
        assertThat(scanned.getValue().getTableName()).isEqualTo("Stores");
        assertThat(scanned.getValue().getProjectionExpression()).isEqualTo("storeId, notifications");
        verify(dynamoDB).putItem(any(PutItemRequest.class));
        verify(dynamoDB).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void movesEachEmbeddedNotificationAsUnreadKeepingTheOldestLowestAndRemovesTheList() {
        // given
        Map<String, AttributeValue> store = store(
                notification("WARNING", "MARKETPLACE_RETURN_UNMATCHED", "ret-1", "Allegro return AL-1 could not be matched"),
                notification("WARNING", "UNAUTHENTICATED", "allegro_marketplace", "Your connection to Allegro marketplace has expired"));
        ArgumentCaptor<PutItemRequest> puts = ArgumentCaptor.forClass(PutItemRequest.class);
        ArgumentCaptor<UpdateItemRequest> update = ArgumentCaptor.forClass(UpdateItemRequest.class);

        // when
        migration.moveNotifications(store, MIGRATED_AT);

        // then
        verify(dynamoDB, times(2)).putItem(puts.capture());
        Map<String, AttributeValue> oldest = puts.getAllValues().get(0).getItem();
        Map<String, AttributeValue> newest = puts.getAllValues().get(1).getItem();
        assertThat(oldest.get("notificationId").getS()).isEqualTo("MARKETPLACE_RETURN_UNMATCHED:ret-1");
        assertThat(oldest.get("createdAt").getS()).isEqualTo("2026-09-14T11:59:59");
        assertThat(newest.get("notificationId").getS()).isEqualTo("UNAUTHENTICATED:allegro_marketplace");
        assertThat(newest.get("createdAt").getS()).isEqualTo("2026-09-14T12:00:00");
        assertThat(newest.get("storeId").getS()).isEqualTo("store-1");
        assertThat(newest.get("unreadStoreId").getS()).isEqualTo("store-1");
        assertThat(newest.get("type").getS()).isEqualTo("UNAUTHENTICATED");
        assertThat(newest.get("severity").getS()).isEqualTo("WARNING");
        assertThat(newest.get("object").getS()).isEqualTo("allegro_marketplace");
        assertThat(newest.get("message").getS()).isEqualTo("Your connection to Allegro marketplace has expired");
        assertThat(newest).doesNotContainKey("readAt");
        assertThat(puts.getAllValues()).allMatch(put -> "StoreNotifications".equals(put.getTableName())
                && "attribute_not_exists(notificationId)".equals(put.getConditionExpression()));
        verify(dynamoDB).updateItem(update.capture());
        assertThat(update.getValue().getTableName()).isEqualTo("Stores");
        assertThat(update.getValue().getKey()).isEqualTo(Map.of("storeId", string("store-1")));
        assertThat(update.getValue().getUpdateExpression()).isEqualTo("REMOVE notifications");
    }

    @Test
    void skipsANotificationAlreadyMovedByAnEarlierRunAndStillRemovesTheList() {
        // given
        when(dynamoDB.putItem(any(PutItemRequest.class)))
                .thenThrow(new ConditionalCheckFailedException("already moved"))
                .thenReturn(new PutItemResult());
        Map<String, AttributeValue> store = store(
                notification("WARNING", "UNAUTHENTICATED", "allegro_marketplace", "Expired"),
                notification("WARNING", "MARKETPLACE_RETURN_UNMATCHED", "ret-1", "Unmatched"));

        // when
        migration.moveNotifications(store, MIGRATED_AT);

        // then
        verify(dynamoDB, times(2)).putItem(any(PutItemRequest.class));
        verify(dynamoDB).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void leavesAStoreWithoutEmbeddedNotificationsUntouched() {
        // when
        migration.moveNotifications(Map.of("storeId", string("store-1")), MIGRATED_AT);

        // then
        verifyNoInteractions(dynamoDB);
    }

    @Test
    void removesAnEmptyNotificationList() {
        // when
        migration.moveNotifications(store(), MIGRATED_AT);

        // then
        verify(dynamoDB, never()).putItem(any(PutItemRequest.class));
        verify(dynamoDB).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void identifiesANotificationWithoutAnObjectByItsMessage() {
        // given
        ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);

        // when
        migration.moveNotifications(store(notification("INFO", "WELCOME", null, "Welcome to CommerceLink")), MIGRATED_AT);

        // then
        verify(dynamoDB).putItem(put.capture());
        assertThat(put.getValue().getItem().get("notificationId").getS()).matches("WELCOME:[0-9a-f]{16}");
        assertThat(put.getValue().getItem()).doesNotContainKey("object");
    }

    @Test
    void treatsTheTableAsReadyOnlyWhenTheTableAndItsIndexAreActive() {
        // when / then
        assertThat(V013_MoveStoreNotificationsToTable.isActive(table("ACTIVE", "ACTIVE"))).isTrue();
        assertThat(V013_MoveStoreNotificationsToTable.isActive(table("ACTIVE", "CREATING"))).isFalse();
        assertThat(V013_MoveStoreNotificationsToTable.isActive(table("CREATING", "CREATING"))).isFalse();
    }

    @Test
    void skipsAMalformedNotificationEntryButMovesTheValidOnesAndRemovesTheList() {
        // given
        Map<String, AttributeValue> store = store(
                notification("WARNING", "BOGUS_TYPE", "obj-1", "Unknown type"),
                notification("WARNING", "UNAUTHENTICATED", "allegro_marketplace", "Expired"));
        ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);

        // when
        migration.moveNotifications(store, MIGRATED_AT);

        // then
        verify(dynamoDB).putItem(put.capture());
        assertThat(put.getValue().getItem().get("notificationId").getS()).isEqualTo("UNAUTHENTICATED:allegro_marketplace");
        verify(dynamoDB).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void resumesWaitingForActiveWhenDescribeTableCannotSeeTheNewTableYet() {
        // given
        when(dynamoDB.describeTable("StoreNotifications"))
                .thenThrow(new ResourceNotFoundException("not visible yet"))
                .thenReturn(new DescribeTableResult().withTable(table("ACTIVE", "ACTIVE")));
        when(dynamoDB.scan(any(ScanRequest.class))).thenReturn(new ScanResult().withItems(List.of()));

        // when
        migration.execute();

        // then
        verify(dynamoDB, times(2)).describeTable("StoreNotifications");
    }

    @Test
    void stopsWaitingForActiveOnceTheDeadlinePasses() {
        // given
        when(dynamoDB.describeTable("StoreNotifications"))
                .thenReturn(new DescribeTableResult().withTable(table("CREATING", "CREATING")));
        V013_MoveStoreNotificationsToTable migrationWithFastDeadline = new V013_MoveStoreNotificationsToTable(
                dynamoDB, millis -> { }, new AdvancingClock(Instant.EPOCH, Duration.ofMinutes(11)));

        // when / then
        assertThatThrownBy(migrationWithFastDeadline::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not become active");
    }
}
