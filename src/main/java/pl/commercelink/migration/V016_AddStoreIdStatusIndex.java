package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateGlobalSecondaryIndexAction;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexUpdate;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

/**
 * Orders by store and status, keys only: the orders list asks for the open statuses one by one and reads only open
 * orders, however large the store's history of Completed and Cancelled orders grows. Built on attributes every order
 * already has, so DynamoDB backfills existing orders itself and keeps the index current on every status change; the
 * list falls back to reading the store's partition while the index is still being built (OrdersRepository).
 */
@ChangeUnit(id = "V016-add-store-id-status-index", order = "016", author = "commercelink")
public class V016_AddStoreIdStatusIndex {

    public static final String INDEX = "StoreIdStatusIndex";

    private final AmazonDynamoDB dynamoDB;

    public V016_AddStoreIdStatusIndex(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void execute() {
        var table = dynamoDB.describeTable("Orders").getTable();
        boolean indexExists = table.getGlobalSecondaryIndexes() != null
                && table.getGlobalSecondaryIndexes().stream().anyMatch(index -> INDEX.equals(index.getIndexName()));
        if (indexExists) {
            return;
        }
        CreateGlobalSecondaryIndexAction create = new CreateGlobalSecondaryIndexAction()
                .withIndexName(INDEX)
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), new KeySchemaElement("status", KeyType.RANGE))
                .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY));
        // A table on provisioned capacity rejects an index without its own throughput (and a failed migration stops the
        // application from starting); tables created by hand may be provisioned, so the index takes the table's figures.
        boolean onDemand = table.getBillingModeSummary() != null
                && BillingMode.PAY_PER_REQUEST.toString().equals(table.getBillingModeSummary().getBillingMode());
        if (!onDemand) {
            ProvisionedThroughputDescription capacity = table.getProvisionedThroughput();
            create.withProvisionedThroughput(new ProvisionedThroughput(
                    Math.max(1L, capacity.getReadCapacityUnits()), Math.max(1L, capacity.getWriteCapacityUnits())));
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName("Orders")
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("status", ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate().withCreate(create)));
    }

    @RollbackExecution
    public void rollback() {
    }
}
