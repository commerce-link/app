package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static pl.commercelink.scheduling.DailyScheduleExecutionCount.COUNTER_KEY;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.EXECUTION_COUNT;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.EXECUTION_DATE;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.EXECUTION_TYPE;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.STORE_ID;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.TABLE_NAME;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.TARGET;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.counterKey;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.dayPrefix;
import static pl.commercelink.scheduling.DailyScheduleExecutionCount.monthPrefix;

@Component
public class DailyScheduleExecutionCountRepository {

    private final AmazonDynamoDB amazonDynamoDB;
    private final DynamoDBMapper dynamoDBMapper;

    public DailyScheduleExecutionCountRepository(AmazonDynamoDB amazonDynamoDB) {
        this.amazonDynamoDB = amazonDynamoDB;
        this.dynamoDBMapper = new DynamoDBMapper(amazonDynamoDB);
    }

    public void increment(String storeId, LocalDate date, ScheduledExecution type, String target) {
        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .addKeyEntry(STORE_ID, new AttributeValue(storeId))
                .addKeyEntry(COUNTER_KEY, new AttributeValue(counterKey(date, type, target)))
                .withUpdateExpression("ADD #count :one SET #date = :date, #type = :type, #target = :target")
                .addExpressionAttributeNamesEntry("#count", EXECUTION_COUNT)
                .addExpressionAttributeNamesEntry("#date", EXECUTION_DATE)
                .addExpressionAttributeNamesEntry("#type", EXECUTION_TYPE)
                .addExpressionAttributeNamesEntry("#target", TARGET)
                .addExpressionAttributeValuesEntry(":one", new AttributeValue().withN("1"))
                .addExpressionAttributeValuesEntry(":date", new AttributeValue(date.toString()))
                .addExpressionAttributeValuesEntry(":type", new AttributeValue(type.name()))
                .addExpressionAttributeValuesEntry(":target", new AttributeValue(target)));
    }

    public List<DailyScheduleExecutionCount> findDay(String storeId, LocalDate date) {
        return findByKeyPrefix(storeId, dayPrefix(date));
    }

    public List<DailyScheduleExecutionCount> findMonth(String storeId, YearMonth month) {
        return findByKeyPrefix(storeId, monthPrefix(month));
    }

    private List<DailyScheduleExecutionCount> findByKeyPrefix(String storeId, String prefix) {
        DailyScheduleExecutionCount hashKey = new DailyScheduleExecutionCount();
        hashKey.setStoreId(storeId);
        return dynamoDBMapper.query(DailyScheduleExecutionCount.class, new DynamoDBQueryExpression<DailyScheduleExecutionCount>()
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition(COUNTER_KEY, new Condition()
                        .withComparisonOperator(ComparisonOperator.BEGINS_WITH)
                        .withAttributeValueList(new AttributeValue(prefix))));
    }
}
