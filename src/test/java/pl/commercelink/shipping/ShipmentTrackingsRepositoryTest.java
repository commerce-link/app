package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pl.commercelink.shipping.tracking.ShipmentTrackingState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentTrackingsRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private ShipmentTrackingsRepository repository;

    @BeforeEach
    void setup() {
        repository = new ShipmentTrackingsRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    void saveIfAbsentUsesConditionalPutOnTrackingNo() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        ArgumentCaptor<DynamoDBSaveExpression> expression = ArgumentCaptor.forClass(DynamoDBSaveExpression.class);

        // when
        boolean saved = repository.saveIfAbsent(tracking);

        // then
        assertThat(saved).isTrue();
        verify(dynamoDBMapper).save(eq(tracking), expression.capture());
        assertThat(expression.getValue().getExpected()).containsKey("trackingNo");
        assertThat(expression.getValue().getExpected().get("trackingNo").getExists()).isFalse();
    }

    @Test
    void saveIfAbsentReturnsFalseWhenEntryExists() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        doThrow(new ConditionalCheckFailedException("exists")).when(dynamoDBMapper).save(eq(tracking), any(DynamoDBSaveExpression.class));

        // when
        boolean saved = repository.saveIfAbsent(tracking);

        // then
        assertThat(saved).isFalse();
    }

    @Test
    void findLoadsByCompositeKey() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        when(dynamoDBMapper.load(ShipmentTracking.class, "store-1", "PKG-1")).thenReturn(tracking);

        // when
        Optional<ShipmentTracking> found = repository.find("store-1", "PKG-1");
        Optional<ShipmentTracking> missing = repository.find("store-1", "PKG-2");

        // then
        assertThat(found).contains(tracking);
        assertThat(missing).isEmpty();
    }

    @Test
    void findAndSaveNormalizeTheTrackingNumberKey() {
        // given
        ShipmentTracking tracking = new ShipmentTracking("store-1", " pkg-1 ", "order-1", null, LocalDateTime.now());

        // when
        repository.find("store-1", "pkg-1");
        boolean saved = repository.saveIfAbsent(tracking);

        // then: Furgonetka echoes numbers upper-cased, so the index key must not depend on how the operator typed it
        verify(dynamoDBMapper).load(ShipmentTracking.class, "store-1", "PKG-1");
        assertThat(saved).isTrue();
        assertThat(tracking.getTrackingNo()).isEqualTo("PKG-1");
    }

    @Test
    void advanceFromNoStateExpectsTheStateAttributeToBeAbsent() {
        // given
        ShipmentTracking row = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now(), "allegro", "shp-1");
        ArgumentCaptor<DynamoDBSaveExpression> expression = ArgumentCaptor.forClass(DynamoDBSaveExpression.class);

        // when
        boolean advanced = repository.advance(row, ShipmentTrackingState.COLLECTED);

        // then
        assertThat(advanced).isTrue();
        assertThat(row.getState()).isEqualTo("COLLECTED");
        verify(dynamoDBMapper).save(eq(row), expression.capture());
        assertThat(expression.getValue().getExpected().get("state").getExists()).isFalse();
    }

    @Test
    void advanceFromCollectedExpectsTheStateItWasReadWith() {
        // given
        ShipmentTracking row = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now(), "allegro", "shp-1");
        row.setState("COLLECTED");
        ArgumentCaptor<DynamoDBSaveExpression> expression = ArgumentCaptor.forClass(DynamoDBSaveExpression.class);

        // when
        repository.advance(row, ShipmentTrackingState.DELIVERED);

        // then
        verify(dynamoDBMapper).save(eq(row), expression.capture());
        assertThat(expression.getValue().getExpected().get("state").getValue().getS()).isEqualTo("COLLECTED");
    }

    @Test
    void advanceLostToAnotherWriterReturnsFalseAndRestoresTheReadState() {
        // given
        ShipmentTracking row = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now(), "allegro", "shp-1");
        doThrow(new ConditionalCheckFailedException("changed")).when(dynamoDBMapper).save(eq(row), any(DynamoDBSaveExpression.class));

        // when
        boolean advanced = repository.advance(row, ShipmentTrackingState.COLLECTED);

        // then
        assertThat(advanced).isFalse();
        assertThat(row.getState()).isNull();
    }

    @Test
    void markPolledKeepsTheStateConditionSoItNeverRevertsAState() {
        // given
        ShipmentTracking row = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now(), "allegro", "shp-1");
        row.setState("COLLECTED");
        LocalDateTime at = LocalDateTime.of(2026, 10, 9, 12, 5);
        ArgumentCaptor<DynamoDBSaveExpression> expression = ArgumentCaptor.forClass(DynamoDBSaveExpression.class);

        // when
        boolean saved = repository.markPolled(row, at);

        // then
        assertThat(saved).isTrue();
        assertThat(row.getLastPolledAt()).isEqualTo(at);
        verify(dynamoDBMapper).save(eq(row), expression.capture());
        assertThat(expression.getValue().getExpected().get("state").getValue().getS()).isEqualTo("COLLECTED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByStoreQueriesTheStorePartition() {
        // given
        PaginatedQueryList<ShipmentTracking> page = mock(PaginatedQueryList.class);
        ShipmentTracking row = new ShipmentTracking("store-1", "PKG-1", "order-1", null, LocalDateTime.now());
        when(page.iterator()).thenReturn(List.of(row).iterator());
        ArgumentCaptor<DynamoDBQueryExpression<ShipmentTracking>> query = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        when(dynamoDBMapper.query(eq(ShipmentTracking.class), query.capture())).thenReturn(page);

        // when
        List<ShipmentTracking> rows = repository.findByStore("store-1");

        // then
        assertThat(rows).containsExactly(row);
        assertThat(query.getValue().getHashKeyValues().getStoreId()).isEqualTo("store-1");
    }
}
