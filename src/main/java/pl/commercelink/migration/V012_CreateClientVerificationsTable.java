package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.TimeToLiveSpecification;
import com.amazonaws.services.dynamodbv2.model.UpdateTimeToLiveRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.createTableIfAbsent;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.hashKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.rangeKey;
import static pl.commercelink.starter.migration.DynamoDbMigrationSupport.stringAttribute;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "V012-create-client-verifications-table", order = "012", author = "commercelink")
public class V012_CreateClientVerificationsTable {

    private static final String TABLE_NAME = "ClientVerifications";

    private final AmazonDynamoDB dynamoDB;

    @Execution
    public void createTable() {
        createTableIfAbsent(dynamoDB, new CreateTableRequest()
                .withTableName(TABLE_NAME)
                .withKeySchema(hashKey("subjectKey"), rangeKey("verificationId"))
                .withAttributeDefinitions(stringAttribute("subjectKey"), stringAttribute("verificationId"))
                .withBillingMode(BillingMode.PAY_PER_REQUEST));

        try {
            dynamoDB.updateTimeToLive(new UpdateTimeToLiveRequest()
                    .withTableName(TABLE_NAME)
                    .withTimeToLiveSpecification(new TimeToLiveSpecification()
                            .withAttributeName("ttl")
                            .withEnabled(true)));
            log.info("Enabled TTL on DynamoDB table: {}", TABLE_NAME);
        } catch (AmazonDynamoDBException e) {
            log.info("Skipping TTL setup on DynamoDB table {}: {}", TABLE_NAME, e.getErrorMessage());
        }
    }

    @RollbackExecution
    public void rollback() {}
}
