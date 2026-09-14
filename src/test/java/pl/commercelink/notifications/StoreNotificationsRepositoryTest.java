package pl.commercelink.notifications;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreNotificationsRepositoryTest {

    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 9, 14, 15, 30);

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private StoreNotificationsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new StoreNotificationsRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    private static Map<String, AttributeValue> key(String notificationId) {
        return Map.of("storeId", new AttributeValue("store-1"), "notificationId", new AttributeValue(notificationId));
    }

    private static StoreNotificationRecord record(String notificationId) {
        StoreNotificationRecord record = new StoreNotificationRecord();
        record.setStoreId("store-1");
        record.setNotificationId(notificationId);
        return record;
    }

    @Test
    void putIfAbsentWritesOnlyANotificationIdThatDoesNotExistYet() {
        // given
        StoreNotificationRecord record = record("UNAUTHENTICATED:allegro_marketplace");
        ArgumentCaptor<DynamoDBSaveExpression> expression = ArgumentCaptor.forClass(DynamoDBSaveExpression.class);

        // when
        boolean saved = repository.putIfAbsent(record);

        // then
        assertThat(saved).isTrue();
        verify(dynamoDBMapper).save(eq(record), expression.capture());
        assertThat(expression.getValue().getExpected()).containsOnlyKeys("notificationId");
        assertThat(expression.getValue().getExpected().get("notificationId").getExists()).isFalse();
    }

    @Test
    void putIfAbsentReportsANotificationThatAlreadyExists() {
        // given
        StoreNotificationRecord record = record("UNAUTHENTICATED:allegro_marketplace");
        doThrow(new ConditionalCheckFailedException("exists"))
                .when(dynamoDBMapper).save(eq(record), any(DynamoDBSaveExpression.class));

        // when / then
        assertThat(repository.putIfAbsent(record)).isFalse();
    }

    @Test
    void findAllReadsEveryPageOfTheStoreWithAConsistentRead() {
        // given
        Map<String, AttributeValue> firstItem = key("A:1");
        Map<String, AttributeValue> secondItem = key("B:2");
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(new QueryResult().withItems(List.of(firstItem)).withLastEvaluatedKey(firstItem))
                .thenReturn(new QueryResult().withItems(List.of(secondItem)));
        when(dynamoDBMapper.marshallIntoObject(StoreNotificationRecord.class, firstItem)).thenReturn(record("A:1"));
        when(dynamoDBMapper.marshallIntoObject(StoreNotificationRecord.class, secondItem)).thenReturn(record("B:2"));
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);

        // when
        List<StoreNotificationRecord> records = repository.findAll("store-1");

        // then
        assertThat(records).extracting(StoreNotificationRecord::getNotificationId).containsExactly("A:1", "B:2");
        verify(amazonDynamoDB, times(2)).query(requests.capture());
        QueryRequest first = requests.getAllValues().get(0);
        assertThat(first.getTableName()).isEqualTo("StoreNotifications");
        assertThat(first.getIndexName()).isNull();
        assertThat(first.getKeyConditionExpression()).isEqualTo("storeId = :storeId");
        assertThat(first.getExpressionAttributeValues().get(":storeId").getS()).isEqualTo("store-1");
        assertThat(first.getConsistentRead()).isTrue();
        assertThat(first.getExclusiveStartKey()).isNull();
        assertThat(requests.getAllValues().get(1).getExclusiveStartKey()).isEqualTo(firstItem);
    }

    @Test
    void findLoadsTheNotificationByItsKey() {
        // given
        StoreNotificationRecord record = record("A:1");
        when(dynamoDBMapper.load(StoreNotificationRecord.class, "store-1", "A:1")).thenReturn(record);

        // when
        var found = repository.find("store-1", "A:1");
        var missing = repository.find("store-1", "B:2");

        // then
        assertThat(found).contains(record);
        assertThat(missing).isEmpty();
    }

    @Test
    void countUnreadAddsUpTheCountOfEveryPageOfTheUnreadIndex() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(new QueryResult().withCount(2).withLastEvaluatedKey(key("A:1")))
                .thenReturn(new QueryResult().withCount(3));
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);

        // when
        long unread = repository.countUnread("store-1");

        // then
        assertThat(unread).isEqualTo(5);
        verify(amazonDynamoDB, times(2)).query(requests.capture());
        QueryRequest first = requests.getAllValues().get(0);
        assertThat(first.getTableName()).isEqualTo("StoreNotifications");
        assertThat(first.getIndexName()).isEqualTo("UnreadByStore");
        assertThat(first.getKeyConditionExpression()).isEqualTo("unreadStoreId = :storeId");
        assertThat(first.getExpressionAttributeValues().get(":storeId").getS()).isEqualTo("store-1");
        assertThat(first.getSelect()).isEqualTo("COUNT");
        assertThat(requests.getAllValues().get(1).getExclusiveStartKey()).isEqualTo(key("A:1"));
    }

    @Test
    void findUnreadIdsReadsEveryPageOfTheUnreadIndex() {
        // given
        when(amazonDynamoDB.query(any(QueryRequest.class)))
                .thenReturn(new QueryResult().withItems(List.of(key("A:1"))).withLastEvaluatedKey(key("A:1")))
                .thenReturn(new QueryResult().withItems(List.of(key("B:2"))));
        ArgumentCaptor<QueryRequest> requests = ArgumentCaptor.forClass(QueryRequest.class);

        // when
        List<String> ids = repository.findUnreadIds("store-1");

        // then
        assertThat(ids).containsExactly("A:1", "B:2");
        verify(amazonDynamoDB, times(2)).query(requests.capture());
        assertThat(requests.getAllValues().get(0).getIndexName()).isEqualTo("UnreadByStore");
        assertThat(requests.getAllValues().get(0).getSelect()).isNull();
    }

    @Test
    void markReadStampsTheReadDateAndTakesTheNotificationOutOfTheUnreadIndex() {
        // given
        ArgumentCaptor<UpdateItemRequest> request = ArgumentCaptor.forClass(UpdateItemRequest.class);

        // when
        boolean marked = repository.markRead("store-1", "A:1", READ_AT);

        // then
        assertThat(marked).isTrue();
        verify(amazonDynamoDB).updateItem(request.capture());
        assertThat(request.getValue().getTableName()).isEqualTo("StoreNotifications");
        assertThat(request.getValue().getKey()).isEqualTo(key("A:1"));
        assertThat(request.getValue().getUpdateExpression()).isEqualTo("SET readAt = :readAt REMOVE unreadStoreId");
        assertThat(request.getValue().getConditionExpression()).isEqualTo("attribute_exists(notificationId)");
        assertThat(request.getValue().getExpressionAttributeValues().get(":readAt").getS()).isEqualTo("2026-09-14T15:30:00");
    }

    @Test
    void markReadReportsANotificationThatNoLongerExists() {
        // given
        doThrow(new ConditionalCheckFailedException("missing")).when(amazonDynamoDB).updateItem(any(UpdateItemRequest.class));

        // when / then
        assertThat(repository.markRead("store-1", "A:1", READ_AT)).isFalse();
    }

    @Test
    void markUnreadClearsTheReadDateAndPutsTheNotificationBackIntoTheUnreadIndex() {
        // given
        ArgumentCaptor<UpdateItemRequest> request = ArgumentCaptor.forClass(UpdateItemRequest.class);

        // when
        boolean marked = repository.markUnread("store-1", "A:1");

        // then
        assertThat(marked).isTrue();
        verify(amazonDynamoDB).updateItem(request.capture());
        assertThat(request.getValue().getKey()).isEqualTo(key("A:1"));
        assertThat(request.getValue().getUpdateExpression()).isEqualTo("SET unreadStoreId = :storeId REMOVE readAt");
        assertThat(request.getValue().getConditionExpression()).isEqualTo("attribute_exists(notificationId)");
        assertThat(request.getValue().getExpressionAttributeValues().get(":storeId").getS()).isEqualTo("store-1");
    }

    @Test
    void markUnreadReportsANotificationThatNoLongerExists() {
        // given
        doThrow(new ConditionalCheckFailedException("missing")).when(amazonDynamoDB).updateItem(any(UpdateItemRequest.class));

        // when / then
        assertThat(repository.markUnread("store-1", "A:1")).isFalse();
    }

    @Test
    void deleteRemovesTheNotificationByItsKey() {
        // given
        ArgumentCaptor<DeleteItemRequest> request = ArgumentCaptor.forClass(DeleteItemRequest.class);

        // when
        repository.delete("store-1", "A:1");

        // then
        verify(amazonDynamoDB).deleteItem(request.capture());
        assertThat(request.getValue().getTableName()).isEqualTo("StoreNotifications");
        assertThat(request.getValue().getKey()).isEqualTo(key("A:1"));
    }
}
