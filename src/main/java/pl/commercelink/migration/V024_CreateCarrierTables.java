package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import pl.commercelink.carriers.Carrier;
import pl.commercelink.carriers.CarrierDictionary;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@RequiredArgsConstructor
@ChangeUnit(id = "V024-create-carrier-tables", order = "024", author = "commercelink")
public class V024_CreateCarrierTables {

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void createTables() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(Carrier.TABLE_NAME)
                .withKeySchema(hashKey("name"))
                .withAttributeDefinitions(stringAttribute("name"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(CarrierDictionary.TABLE_NAME)
                .withKeySchema(hashKey("dictionary"))
                .withAttributeDefinitions(stringAttribute("dictionary"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));
    }

    @RollbackExecution
    public void rollback() {}
}
