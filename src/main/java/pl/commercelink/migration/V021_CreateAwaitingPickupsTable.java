package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@ChangeUnit(id = "V021-create-awaiting-pickups-table", order = "021", author = "commercelink")
public class V021_CreateAwaitingPickupsTable {

    private final AmazonDynamoDB dynamoDB;

    public V021_CreateAwaitingPickupsTable(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("AwaitingPickups")
                .withKeySchema(hashKey("storeId"), rangeKey("externalId"))
                .withAttributeDefinitions(stringAttribute("storeId"), stringAttribute("externalId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
