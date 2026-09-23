package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.receipts.ReceiptAttempt;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.allProjection;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@ChangeUnit(id = "V016-create-receipt-attempts-table", order = "016", author = "commercelink")
public class V016_CreateReceiptAttemptsTable {

    private final AmazonDynamoDB dynamoDB;

    public V016_CreateReceiptAttemptsTable(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(ReceiptAttempt.TABLE_NAME)
                .withKeySchema(hashKey("storeId"), rangeKey("receiptKey"))
                .withAttributeDefinitions(
                        stringAttribute("storeId"),
                        stringAttribute("receiptKey"),
                        stringAttribute("dueBucket"),
                        stringAttribute("nextCheckAt"))
                .withGlobalSecondaryIndexes(new GlobalSecondaryIndex()
                        .withIndexName(ReceiptAttempt.DUE_INDEX)
                        .withKeySchema(hashKey("dueBucket"), rangeKey("nextCheckAt"))
                        .withProjection(allProjection()))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
