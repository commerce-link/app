package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.PointInTimeRecoverySpecification;
import com.amazonaws.services.dynamodbv2.model.UpdateContinuousBackupsRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.taxonomy.PendingCategorization;
import pl.commercelink.taxonomy.TaxonomyItem;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@ChangeUnit(id = "V016-create-taxonomy-tables", order = "016", author = "commercelink")
@Slf4j
public class V016_CreateTaxonomyTables {

    private final AmazonDynamoDB dynamoDB;

    public V016_CreateTaxonomyTables(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTables() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(TaxonomyItem.TABLE_NAME)
                .withKeySchema(hashKey(TaxonomyItem.MFN))
                .withAttributeDefinitions(stringAttribute(TaxonomyItem.MFN))
                .withBillingMode(BillingMode.PAY_PER_REQUEST)
                .withDeletionProtectionEnabled(true));

        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(PendingCategorization.TABLE_NAME)
                .withKeySchema(hashKey(PendingCategorization.MFN))
                .withAttributeDefinitions(stringAttribute(PendingCategorization.MFN))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));

        enablePointInTimeRecovery(TaxonomyItem.TABLE_NAME);
    }

    private void enablePointInTimeRecovery(String tableName) {
        try {
            dynamoDB.waiters().tableExists().run(
                    new com.amazonaws.waiters.WaiterParameters<>(new DescribeTableRequest(tableName)));
            dynamoDB.updateContinuousBackups(new UpdateContinuousBackupsRequest()
                    .withTableName(tableName)
                    .withPointInTimeRecoverySpecification(
                            new PointInTimeRecoverySpecification().withPointInTimeRecoveryEnabled(true)));
            log.info("Enabled point-in-time recovery on DynamoDB table: {}", tableName);
        } catch (RuntimeException e) {
            log.warn("Could not enable point-in-time recovery on {}: {}", tableName, e.getMessage());
        }
    }

    @RollbackExecution
    public void rollback() {}
}
