package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.*;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListKey;

import java.util.List;
import java.util.Map;

/**
 * Deliveries by store and list key (spec §7): the deliveries list reads the deliveries on their way, the settlement
 * backlog and a window of the history as key ranges, however long the store's history grows. The key is new, so the
 * existing deliveries get it here once; every later save recomputes it (Delivery.getListKey). Only listKey is written,
 * without touching version, so an operator saving a delivery meanwhile is not refused by optimistic locking. The list
 * reads the store's partition until the index is active (DeliveriesRepository).
 */
@ChangeUnit(id = "V019-add-deliveries-list-key-index", order = "019", author = "commercelink")
public class V019_AddDeliveriesListKeyIndex {

    public static final String INDEX = "StoreIdListKeyIndex";
    static final List<String> PROJECTED = List.of("provider", "counterpartyShortcut", "type", "orderStatus",
            "orderErrorMessage", "tracking", "estimatedDeliveryAt", "orderedAt", "receivedAt", "externalDeliveryId",
            "totalCost", "tax", "invoiced", "synced", "paid", "connectionMode");

    private final AmazonDynamoDB dynamoDB;

    public V019_AddDeliveriesListKeyIndex(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
    }

    @Execution
    public void execute() {
        createIndexIfAbsent();
        backfillListKeys();
    }

    private void createIndexIfAbsent() {
        var table = dynamoDB.describeTable("Deliveries").getTable();
        boolean indexExists = table.getGlobalSecondaryIndexes() != null
                && table.getGlobalSecondaryIndexes().stream().anyMatch(index -> INDEX.equals(index.getIndexName()));
        if (indexExists) {
            return;
        }
        CreateGlobalSecondaryIndexAction create = new CreateGlobalSecondaryIndexAction()
                .withIndexName(INDEX)
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), new KeySchemaElement("listKey", KeyType.RANGE))
                .withProjection(new Projection().withProjectionType(ProjectionType.INCLUDE).withNonKeyAttributes(PROJECTED));
        // Same as V018: a provisioned table rejects an index without its own throughput.
        boolean onDemand = table.getBillingModeSummary() != null
                && BillingMode.PAY_PER_REQUEST.toString().equals(table.getBillingModeSummary().getBillingMode());
        if (!onDemand) {
            ProvisionedThroughputDescription capacity = table.getProvisionedThroughput();
            create.withProvisionedThroughput(new ProvisionedThroughput(
                    Math.max(1L, capacity.getReadCapacityUnits()), Math.max(1L, capacity.getWriteCapacityUnits())));
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName("Deliveries")
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition("listKey", ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate().withCreate(create)));
    }

    private void backfillListKeys() {
        for (Delivery delivery : new DynamoDBMapper(dynamoDB).scan(Delivery.class, new DynamoDBScanExpression())) {
            try {
                dynamoDB.updateItem(new UpdateItemRequest()
                        .withTableName("Deliveries")
                        .withKey(Map.of("storeId", new AttributeValue(delivery.getStoreId()),
                                "deliveryId", new AttributeValue(delivery.getDeliveryId())))
                        .withUpdateExpression("SET listKey = :key")
                        .withConditionExpression("attribute_exists(deliveryId)")
                        .withExpressionAttributeValues(Map.of(":key", new AttributeValue(DeliveryListKey.of(delivery)))));
            } catch (ConditionalCheckFailedException deletedMeanwhile) {
                // removed between the scan and the update: nothing to key
            }
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
