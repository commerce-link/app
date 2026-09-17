package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.scheduling.ScheduledDailyExecutionCounters;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@ChangeUnit(id = "V015-create-scheduled-daily-execution-counters-table", order = "015", author = "commercelink")
public class V015_CreateScheduledDailyExecutionCountersTable {

    private final AmazonDynamoDB dynamoDB;

    public V015_CreateScheduledDailyExecutionCountersTable(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(ScheduledDailyExecutionCounters.TABLE_NAME)
                .withKeySchema(
                        hashKey(ScheduledDailyExecutionCounters.STORE_ID_ATTRIBUTE),
                        rangeKey(ScheduledDailyExecutionCounters.DATE_ATTRIBUTE))
                .withAttributeDefinitions(
                        stringAttribute(ScheduledDailyExecutionCounters.STORE_ID_ATTRIBUTE),
                        stringAttribute(ScheduledDailyExecutionCounters.DATE_ATTRIBUTE))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
