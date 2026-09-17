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
import java.util.HashMap;
import java.util.List;

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
        AttributeValue store = new AttributeValue().withS(storeId);
        AttributeValue day = new AttributeValue().withS(formatDate(date));

        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .addKeyEntry(STORE_ID_ATTRIBUTE, store)
                .addKeyEntry(DATE_ATTRIBUTE, day)
                .withUpdateExpression("SET #counters = if_not_exists(#counters, :empty)")
                .addExpressionAttributeNamesEntry("#counters", scheduledExecution.getAttributeName())
                .addExpressionAttributeValuesEntry(":empty", new AttributeValue().withM(new HashMap<>())));

        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .addKeyEntry(STORE_ID_ATTRIBUTE, store)
                .addKeyEntry(DATE_ATTRIBUTE, day)
                .withUpdateExpression("SET #counters.#dimension = if_not_exists(#counters.#dimension, :zero) + :one")
                .addExpressionAttributeNamesEntry("#counters", scheduledExecution.getAttributeName())
                .addExpressionAttributeNamesEntry("#dimension", dimension)
                .addExpressionAttributeValuesEntry(":zero", new AttributeValue().withN("0"))
                .addExpressionAttributeValuesEntry(":one", new AttributeValue().withN("1")));
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
