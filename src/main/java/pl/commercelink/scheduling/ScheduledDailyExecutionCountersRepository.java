package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.DATE_ATTRIBUTE;
import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.STORE_ID_ATTRIBUTE;
import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.TABLE_NAME;
import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.formatDate;

@Component
public class ScheduledDailyExecutionCountersRepository extends DynamoDbRepository<ScheduledDailyExecutionCounters> {

    public ScheduledDailyExecutionCountersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public void increment(String storeId, LocalDate date, ScheduledExecution scheduledExecution, String dimension) {
        Map<String, AttributeValue> key = Map.of(
                STORE_ID_ATTRIBUTE, new AttributeValue().withS(storeId),
                DATE_ATTRIBUTE, new AttributeValue().withS(formatDate(date)));

        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key)
                .withUpdateExpression("SET #counters = if_not_exists(#counters, :empty)")
                .withExpressionAttributeNames(Map.of("#counters", scheduledExecution.getAttributeName()))
                .withExpressionAttributeValues(Map.of(":empty", new AttributeValue().withM(Map.of()))));

        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .withKey(key)
                .withUpdateExpression("SET #counters.#dimension = if_not_exists(#counters.#dimension, :zero) + :one")
                .withExpressionAttributeNames(Map.of(
                        "#counters", scheduledExecution.getAttributeName(),
                        "#dimension", dimension))
                .withExpressionAttributeValues(Map.of(
                        ":zero", new AttributeValue().withN("0"),
                        ":one", new AttributeValue().withN("1"))));
    }

    public List<ScheduledDailyExecutionCounters> findAll(String storeId) {
        return dynamoDBMapper.query(ScheduledDailyExecutionCounters.class, new DynamoDBQueryExpression<ScheduledDailyExecutionCounters>()
                .withHashKeyValues(storeKey(storeId))
                .withConsistentRead(false));
    }

    public List<ScheduledDailyExecutionCounters> findBetween(String storeId, LocalDate fromInclusive, LocalDate toInclusive) {
        Condition withinDates = new Condition()
                .withComparisonOperator(ComparisonOperator.BETWEEN)
                .withAttributeValueList(
                        new AttributeValue().withS(formatDate(fromInclusive)),
                        new AttributeValue().withS(formatDate(toInclusive)));

        return dynamoDBMapper.query(ScheduledDailyExecutionCounters.class, new DynamoDBQueryExpression<ScheduledDailyExecutionCounters>()
                .withHashKeyValues(storeKey(storeId))
                .withRangeKeyCondition(DATE_ATTRIBUTE, withinDates)
                .withConsistentRead(false));
    }

    private ScheduledDailyExecutionCounters storeKey(String storeId) {
        ScheduledDailyExecutionCounters key = new ScheduledDailyExecutionCounters();
        key.setStoreId(storeId);
        return key;
    }
}
