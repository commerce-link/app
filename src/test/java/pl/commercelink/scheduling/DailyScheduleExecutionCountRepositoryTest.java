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
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyScheduleExecutionCountRepositoryTest {

    @Mock
    private AmazonDynamoDB amazonDynamoDB;
    @Mock
    private DynamoDBMapper dynamoDBMapper;

    private DailyScheduleExecutionCountRepository repository;

    @BeforeEach
    void setup() {
        repository = new DailyScheduleExecutionCountRepository(amazonDynamoDB);
        ReflectionTestUtils.setField(repository, "dynamoDBMapper", dynamoDBMapper);
    }

    @SuppressWarnings("unchecked")
    private Condition capturedCounterKeyCondition() {
        ArgumentCaptor<DynamoDBQueryExpression<DailyScheduleExecutionCount>> query = ArgumentCaptor.forClass(DynamoDBQueryExpression.class);
        verify(dynamoDBMapper).query(eq(DailyScheduleExecutionCount.class), query.capture());
        assertThat(query.getValue().getHashKeyValues().getStoreId()).isEqualTo("store-1");
        return query.getValue().getRangeKeyConditions().get("counterKey");
    }

    @Test
    void oneAtomicCallRaisesTheCountOfTheDayTypeAndTarget() {
        // given
        ArgumentCaptor<UpdateItemRequest> request = ArgumentCaptor.forClass(UpdateItemRequest.class);

        // when
        repository.increment("store-1", LocalDate.of(2026, 9, 17), ScheduledExecution.ORDERS_IMPORT, "Allegro");

        // then
        verify(amazonDynamoDB).updateItem(request.capture());
        UpdateItemRequest sent = request.getValue();
        assertThat(sent.getTableName()).isEqualTo("DailyScheduleExecutionCounts");
        assertThat(sent.getKey()).isEqualTo(Map.of(
                "storeId", new AttributeValue("store-1"),
                "counterKey", new AttributeValue("2026-09-17#ORDERS_IMPORT#Allegro")));
        assertThat(sent.getUpdateExpression()).isEqualTo("ADD #count :one SET #date = :date, #type = :type, #target = :target");
        assertThat(sent.getExpressionAttributeNames()).containsOnly(
                Map.entry("#count", "executionCount"),
                Map.entry("#date", "executionDate"),
                Map.entry("#type", "executionType"),
                Map.entry("#target", "target"));
        assertThat(sent.getExpressionAttributeValues()).containsOnly(
                Map.entry(":one", new AttributeValue().withN("1")),
                Map.entry(":date", new AttributeValue("2026-09-17")),
                Map.entry(":type", new AttributeValue("ORDERS_IMPORT")),
                Map.entry(":target", new AttributeValue("Allegro")));
    }

    @Test
    void findDayReadsEveryCountOfTheStoreStartingWithThatDate() {
        // given
        when(dynamoDBMapper.query(eq(DailyScheduleExecutionCount.class), any())).thenReturn(mock(PaginatedQueryList.class));

        // when
        repository.findDay("store-1", LocalDate.of(2026, 9, 7));

        // then
        Condition condition = capturedCounterKeyCondition();
        assertThat(condition.getComparisonOperator()).isEqualTo(ComparisonOperator.BEGINS_WITH.name());
        assertThat(condition.getAttributeValueList()).containsExactly(new AttributeValue("2026-09-07#"));
    }

    @Test
    void findMonthReadsEveryCountOfTheStoreStartingWithThatMonth() {
        // given
        when(dynamoDBMapper.query(eq(DailyScheduleExecutionCount.class), any())).thenReturn(mock(PaginatedQueryList.class));

        // when
        repository.findMonth("store-1", YearMonth.of(2026, 9));

        // then
        Condition condition = capturedCounterKeyCondition();
        assertThat(condition.getComparisonOperator()).isEqualTo(ComparisonOperator.BEGINS_WITH.name());
        assertThat(condition.getAttributeValueList()).containsExactly(new AttributeValue("2026-09-"));
    }
}
