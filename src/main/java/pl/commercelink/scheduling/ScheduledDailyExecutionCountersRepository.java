package pl.commercelink.scheduling;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.DATE_ATTRIBUTE;
import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.STORE_ID_ATTRIBUTE;
import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.TABLE_NAME;
import static pl.commercelink.scheduling.ScheduledDailyExecutionCounters.formatDate;

@Component
@RequiredArgsConstructor
public class ScheduledDailyExecutionCountersRepository {

    private final AmazonDynamoDB amazonDynamoDB;

    public void increment(String storeId, LocalDate date, ScheduledExecution scheduledExecution, String dimension) {
        amazonDynamoDB.updateItem(new UpdateItemRequest()
                .withTableName(TABLE_NAME)
                .addKeyEntry(STORE_ID_ATTRIBUTE, new AttributeValue().withS(storeId))
                .addKeyEntry(DATE_ATTRIBUTE, new AttributeValue().withS(formatDate(date)))
                .withUpdateExpression("ADD #type :one, #integration :one")
                .addExpressionAttributeNamesEntry("#type", scheduledExecution.getAttributeName())
                .addExpressionAttributeNamesEntry("#integration", scheduledExecution.attributeNameFor(dimension))
                .addExpressionAttributeValuesEntry(":one", new AttributeValue().withN("1")));
    }
}
