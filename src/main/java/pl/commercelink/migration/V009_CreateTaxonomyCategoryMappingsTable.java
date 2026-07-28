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

@ChangeUnit(id = "V009-create-taxonomy-category-mappings-table", order = "009", author = "commercelink")
public class V009_CreateTaxonomyCategoryMappingsTable {

    private final AmazonDynamoDB dynamoDB;

    public V009_CreateTaxonomyCategoryMappingsTable(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName("TaxonomyCategoryMappings")
                .withKeySchema(hashKey("supplier"), rangeKey("rawCategory"))
                .withAttributeDefinitions(stringAttribute("supplier"), stringAttribute("rawCategory"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
