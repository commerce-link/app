package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.externaloffers.ExternalOfferLink;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@RequiredArgsConstructor
@ChangeUnit(id = "V025-create-external-offer-links-table", order = "025", author = "commercelink")
public class V025_CreateExternalOfferLinksTable {

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(ExternalOfferLink.TABLE_NAME)
                .withKeySchema(hashKey("storeId"), rangeKey("linkKey"))
                .withAttributeDefinitions(
                        stringAttribute("storeId"),
                        stringAttribute("linkKey"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
