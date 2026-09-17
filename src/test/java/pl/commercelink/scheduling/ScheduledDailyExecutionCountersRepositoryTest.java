package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledDailyExecutionCountersRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private ScheduledDailyExecutionCountersRepository repository;

    @BeforeEach
    void setup() {
        repository = new ScheduledDailyExecutionCountersRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @Test
    void incrementCreatesTheCounterMapThenAddsOneToTheDimensionAtomically() {
        // given
        ArgumentCaptor<UpdateItemRequest> requests = ArgumentCaptor.forClass(UpdateItemRequest.class);

        // when
        repository.increment("store-1", DAY, ScheduledExecution.ORDERS_IMPORT, "Allegro");

        // then
        verify(amazonDynamoDB, times(2)).updateItem(requests.capture());
        List<UpdateItemRequest> sent = requests.getAllValues();
        Map<String, AttributeValue> expectedKey = Map.of(
                "storeId", new AttributeValue().withS("store-1"),
                "executionDate", new AttributeValue().withS("2026-09-16"));

        UpdateItemRequest ensureMap = sent.get(0);
        assertThat(ensureMap.getTableName()).isEqualTo("ScheduledDailyExecutionCounters");
        assertThat(ensureMap.getKey()).isEqualTo(expectedKey);
        assertThat(ensureMap.getUpdateExpression()).isEqualTo("SET #counters = if_not_exists(#counters, :empty)");
        assertThat(ensureMap.getExpressionAttributeNames()).containsEntry("#counters", "ordersImport");

        UpdateItemRequest addOne = sent.get(1);
        assertThat(addOne.getKey()).isEqualTo(expectedKey);
        assertThat(addOne.getUpdateExpression())
                .isEqualTo("SET #counters.#dimension = if_not_exists(#counters.#dimension, :zero) + :one");
        assertThat(addOne.getExpressionAttributeNames())
                .containsEntry("#counters", "ordersImport")
                .containsEntry("#dimension", "Allegro");
        assertThat(addOne.getExpressionAttributeValues().get(":one").getN()).isEqualTo("1");
    }

    @Test
    void findBetweenBoundsTheDateRangeInclusively() {
        // given
        when(dynamoDBMapper.query(eq(ScheduledDailyExecutionCounters.class), any())).thenReturn(mock(PaginatedQueryList.class));

        // when
        repository.findBetween("store-1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // then
        DynamoDBQueryExpression<ScheduledDailyExecutionCounters> query = capturedQuery();
        assertThat(query.getHashKeyValues().getStoreId()).isEqualTo("store-1");
        Condition condition = query.getRangeKeyConditions().get("executionDate");
        assertThat(condition.getComparisonOperator()).isEqualTo(ComparisonOperator.BETWEEN.name());
        assertThat(condition.getAttributeValueList())
                .extracting(AttributeValue::getS)
                .containsExactly("2026-09-01", "2026-09-30");
    }

    @Test
    void findAllQueriesTheWholeStorePartition() {
        // given
        when(dynamoDBMapper.query(eq(ScheduledDailyExecutionCounters.class), any())).thenReturn(mock(PaginatedQueryList.class));

        // when
        repository.findAll("store-1");

        // then
        DynamoDBQueryExpression<ScheduledDailyExecutionCounters> query = capturedQuery();
        assertThat(query.getHashKeyValues().getStoreId()).isEqualTo("store-1");
        assertThat(query.getRangeKeyConditions()).isNull();
    }

    @SuppressWarnings("unchecked")
    private DynamoDBQueryExpression<ScheduledDailyExecutionCounters> capturedQuery() {
        ArgumentCaptor<DynamoDBQueryExpression<ScheduledDailyExecutionCounters>> query =
                ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(dynamoDBMapper).query(eq(ScheduledDailyExecutionCounters.class), query.capture());
        return query.getValue();
    }
}
