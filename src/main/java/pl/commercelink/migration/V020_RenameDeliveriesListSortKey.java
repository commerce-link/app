package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DeliveryListKey;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * Renames the deliveries list key to deliveryListSortKey (readable prefixes IN_TRANSIT# / TO_SETTLE# / SETTLED#) and
 * replaces StoreIdListKeyIndex by StoreIdDeliveryListSortKeyIndex. Every step is idempotent and the order matters:
 * DynamoDB allows one online index create or delete per table at a time, so the unit first waits for the table to be
 * idle (V019 may still be building its index on a fresh environment), then deletes the old index and waits until it is
 * gone, then rewrites the keys while no index exists (no write amplification, and the new index builds over fully
 * keyed items), and only then creates the new index without waiting for it.
 * <p>
 * Waits are bounded. Mongock is an ApplicationRunner, so a timeout fails start-up with the index named in the message
 * and the next start resumes where this one stopped. Until the new index is ACTIVE the deliveries list reads fail.
 */
@ChangeUnit(id = "V020-rename-deliveries-list-sort-key", order = "020", author = "commercelink")
public class V020_RenameDeliveriesListSortKey {

    public static final String NEW_INDEX = "StoreIdDeliveryListSortKeyIndex";
    // The shape V019 created (already applied in production). Literals, not V019's constants: an applied change unit is
    // never edited, and this one must keep undoing exactly what V019 wrote.
    static final String OLD_INDEX = "StoreIdListKeyIndex";
    static final String OLD_ATTRIBUTE = "listKey";
    static final List<String> PROJECTED = List.of("provider", "counterpartyShortcut", "type", "orderStatus",
            "orderErrorMessage", "tracking", "estimatedDeliveryAt", "orderedAt", "receivedAt", "externalDeliveryId",
            "totalCost", "tax", "invoiced", "synced", "paid", "connectionMode");
    static final String NEW_ATTRIBUTE = "deliveryListSortKey";
    static final Duration TIMEOUT = Duration.ofMinutes(10);
    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final AmazonDynamoDB dynamoDB;
    private final LongConsumer pause;
    private final Duration timeout;

    @ChangeUnitConstructor
    public V020_RenameDeliveriesListSortKey(AmazonDynamoDB dynamoDB) {
        this(dynamoDB, V020_RenameDeliveriesListSortKey::sleep, TIMEOUT);
    }

    V020_RenameDeliveriesListSortKey(AmazonDynamoDB dynamoDB, LongConsumer pause, Duration timeout) {
        this.dynamoDB = dynamoDB;
        this.pause = pause;
        this.timeout = timeout;
    }

    @Execution
    public void execute() {
        awaitNoIndexOperationInProgress();
        dropOldIndexIfPresent();
        backfillDeliveryListSortKeys();
        createNewIndexIfAbsent();
    }

    private void dropOldIndexIfPresent() {
        if (!hasIndex(describe(), OLD_INDEX)) {
            return;
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName("Deliveries")
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate()
                        .withDelete(new DeleteGlobalSecondaryIndexAction().withIndexName(OLD_INDEX))));
        awaitUntil(table -> !hasIndex(table, OLD_INDEX), "deletion of " + OLD_INDEX);
    }

    private void createNewIndexIfAbsent() {
        if (hasIndex(describe(), NEW_INDEX)) {
            return;
        }
        awaitNoIndexOperationInProgress();
        TableDescription table = describe();
        CreateGlobalSecondaryIndexAction create = new CreateGlobalSecondaryIndexAction()
                .withIndexName(NEW_INDEX)
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), new KeySchemaElement(NEW_ATTRIBUTE, KeyType.RANGE))
                .withProjection(new Projection().withProjectionType(ProjectionType.INCLUDE)
                        .withNonKeyAttributes(PROJECTED));
        // A provisioned table rejects an index without its own throughput (same as V018 and V019).
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
                        new AttributeDefinition(NEW_ATTRIBUTE, ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate().withCreate(create)));
    }

    /**
     * Scans page by page with a narrow projection (a mapper scan list caches every item it iterates, which would keep
     * the whole table in memory at startup). An item that already carries its current key and no old one is skipped.
     */
    private void backfillDeliveryListSortKeys() {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        Map<String, AttributeValue> startKey = null;
        do {
            ScanResult page = dynamoDB.scan(new ScanRequest()
                    .withTableName("Deliveries")
                    .withProjectionExpression("storeId, deliveryId, provider, invoiced, receivedAt, estimatedDeliveryAt, "
                            + NEW_ATTRIBUTE + ", " + OLD_ATTRIBUTE + ", #v")
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExclusiveStartKey(startKey));
            for (Map<String, AttributeValue> item : page.getItems()) {
                Delivery scanned = mapper.marshallIntoObject(Delivery.class, item);
                if (isMigrated(item, scanned)) {
                    continue;
                }
                backfillDeliveryListSortKey(scanned, item.containsKey("version"));
            }
            startKey = page.getLastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
    }

    private static boolean isMigrated(Map<String, AttributeValue> item, Delivery scanned) {
        AttributeValue stored = item.get(NEW_ATTRIBUTE);
        return !item.containsKey(OLD_ATTRIBUTE) && stored != null && DeliveryListKey.of(scanned).equals(stored.getS());
    }

    /**
     * Writes the key computed from the scanned copy only while the delivery is still at the version the scan saw: a save
     * in between already wrote a fresh key, which a stale one must not overwrite (the delivery would be listed in the
     * wrong part of the list until its next save). The version itself is never modified.
     */
    public void backfillDeliveryListSortKey(Delivery scanned, boolean versioned) {
        Map<String, AttributeValue> values = new HashMap<>();
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
                    .withUpdateExpression("SET " + NEW_ATTRIBUTE + " = :key REMOVE " + OLD_ATTRIBUTE)
                    .withConditionExpression(condition)
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExpressionAttributeValues(values));
        } catch (ConditionalCheckFailedException changedMeanwhile) {
            // deleted or saved between the scan and the update: a save already wrote the current key
        }
    }

    private void awaitNoIndexOperationInProgress() {
        awaitUntil(table -> busyIndexes(table).isEmpty() && isActive(table.getTableStatus()),
                "the index operations on Deliveries to finish");
    }

    private static List<String> busyIndexes(TableDescription table) {
        if (table.getGlobalSecondaryIndexes() == null) {
            return List.of();
        }
        return table.getGlobalSecondaryIndexes().stream()
                .filter(index -> !isActive(index.getIndexStatus()))
                .map(index -> index.getIndexName() + " (" + index.getIndexStatus() + ")")
                .toList();
    }

    // dynamodb-local reports no status at all for an index that is ready
    private static boolean isActive(String status) {
        return status == null || "ACTIVE".equals(status);
    }

    private static boolean hasIndex(TableDescription table, String name) {
        return table.getGlobalSecondaryIndexes() != null
                && table.getGlobalSecondaryIndexes().stream().anyMatch(index -> name.equals(index.getIndexName()));
    }

    /**
     * Polls DescribeTable every POLL_INTERVAL until the condition holds; the bound counts the pauses, so a test that
     * passes a no-op pause runs instantly. DynamoDB runs one index create or delete per table at a time (otherwise:
     * "Only 1 online index can be created or deleted simultaneously per table"), so this unit never starts an operation
     * while another one runs. On timeout the start-up fails with the index named; the next start resumes, every step
     * being idempotent.
     */
    private void awaitUntil(Predicate<TableDescription> done, String what) {
        long waitedMillis = 0;
        while (true) {
            TableDescription table = describe();
            if (done.test(table)) {
                return;
            }
            if (waitedMillis >= timeout.toMillis()) {
                throw new IllegalStateException("Timed out after " + timeout + " waiting for " + what + " on Deliveries; "
                        + "indexes: " + describeIndexes(table) + ". Restart the application to resume V020.");
            }
            pause.accept(POLL_INTERVAL.toMillis());
            waitedMillis += POLL_INTERVAL.toMillis();
        }
    }

    private static String describeIndexes(TableDescription table) {
        List<String> busy = busyIndexes(table);
        if (!busy.isEmpty()) {
            return String.join(", ", busy);
        }
        return table.getGlobalSecondaryIndexes() == null ? "none" : table.getGlobalSecondaryIndexes().stream()
                .map(GlobalSecondaryIndexDescription::getIndexName).toList().toString();
    }

    private TableDescription describe() {
        return dynamoDB.describeTable("Deliveries").getTable();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Deliveries indexes", e);
        }
    }

    @RollbackExecution
    public void rollback() {
    }
}
