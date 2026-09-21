package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.scheduling.DailyScheduleExecutionCount;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@ChangeUnit(id = "V015-create-daily-schedule-execution-counts-table", order = "015", author = "commercelink")
public class V015_CreateDailyScheduleExecutionCountsTable {

    private final AmazonDynamoDB dynamoDB;

    public V015_CreateDailyScheduleExecutionCountsTable(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(DailyScheduleExecutionCount.TABLE_NAME)
                .withKeySchema(
                        hashKey(DailyScheduleExecutionCount.STORE_ID),
                        rangeKey(DailyScheduleExecutionCount.COUNTER_KEY))
                .withAttributeDefinitions(
                        stringAttribute(DailyScheduleExecutionCount.STORE_ID),
                        stringAttribute(DailyScheduleExecutionCount.COUNTER_KEY))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
