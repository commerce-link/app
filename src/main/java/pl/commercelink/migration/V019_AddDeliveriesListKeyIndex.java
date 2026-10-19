package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
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
 * existing deliveries get it here once, before the index is created; every later save recomputes it (Delivery.getListKey). Only listKey is written,
 * without touching version, so an operator saving a delivery meanwhile is not refused by optimistic locking. The list
 * reads the store's partition until the index is active (DeliveriesRepository). The backfill runs before the index
 * is created, so the index build never sees an unkeyed delivery and the fallback covers the whole build.
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
        // keys first: the index then builds over fully keyed items, and until it is active the repository fallback
        // (DeliveriesRepository) already reads correct keys for the whole build
        backfillListKeys();
        createIndexIfAbsent();
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

    /**
     * Scans page by page with a narrow projection (a mapper scan list caches every item it iterates, which would keep
     * the whole table in memory at startup) and only what DeliveryListKey needs.
     */
    private void backfillListKeys() {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        Map<String, AttributeValue> startKey = null;
        do {
            ScanResult page = dynamoDB.scan(new ScanRequest()
                    .withTableName("Deliveries")
                    .withProjectionExpression("storeId, deliveryId, provider, invoiced, receivedAt, estimatedDeliveryAt, #v")
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExclusiveStartKey(startKey));
            for (Map<String, AttributeValue> item : page.getItems()) {
                backfillListKey(mapper.marshallIntoObject(Delivery.class, item), item.containsKey("version"));
            }
            startKey = page.getLastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
    }

    /**
     * Writes the key computed from the scanned copy only while the delivery is still at the version the scan saw: a save
     * in between already wrote a fresh key, which a stale one must not overwrite (the delivery would vanish from the
     * list until its next save). The version itself is never modified.
     */
    public void backfillListKey(Delivery scanned, boolean versioned) {
        Map<String, AttributeValue> values = new java.util.HashMap<>();
        values.put(":key", new AttributeValue(DeliveryListKey.of(scanned)));
        String condition = "attribute_exists(deliveryId) AND attribute_not_exists(#v)";
        if (versioned) {
            condition = "attribute_exists(deliveryId) AND #v = :v";
            values.put(":v", new AttributeValue().withN(String.valueOf(scanned.getVersion())));
        }
        try {
            dynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName("Deliveries")
                    .withKey(Map.of("storeId", new AttributeValue(scanned.getStoreId()),
                            "deliveryId", new AttributeValue(scanned.getDeliveryId())))
                    .withUpdateExpression("SET listKey = :key")
                    .withConditionExpression(condition)
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExpressionAttributeValues(values));
        } catch (ConditionalCheckFailedException changedMeanwhile) {
            // deleted or saved between the scan and the update: a save already wrote the current key
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
