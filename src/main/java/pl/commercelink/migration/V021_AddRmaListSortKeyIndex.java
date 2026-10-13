package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RmaListSortKey;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Predicate;

/**
 * RMAs by store and list key (RMA list spec §5.2): the list reads the RMAs being handled and a window of the closed
 * history as key ranges, however long the history grows. The key is new, so existing RMAs get it here once, before the
 * index is created (the build then sees only keyed items); every later save recomputes it (RMA.getRmaListSortKey).
 * Only the key is written, guarded by the version the scan saw and without changing it, so an operator saving an RMA
 * meanwhile is neither overwritten nor refused by optimistic locking.
 * <p>
 * DynamoDB runs one index create or delete per table at a time, so the unit first waits for the table to be idle.
 * It does not wait for the new index to become ACTIVE: until then RMARepository reads the store's partition.
 * Runs on every start (runAlways): production deploys rolling, so an old instance keeps writing RMAs without the key
 * (or leaving a stale prefix after a status change) while the first new instance runs this unit, and a rollback does
 * the same; the next start repairs them. Cost: one narrow-projection scan per start, writes only for stale or missing
 * keys, and index creation is skipped when it exists.
 * <p>
 * Waits are bounded; Mongock is an ApplicationRunner, so a timeout fails start-up and the next start resumes.
 */
@ChangeUnit(id = "V021-add-rma-list-sort-key-index", order = "021", author = "commercelink", runAlways = true)
public class V021_AddRmaListSortKeyIndex {

    public static final String INDEX = "StoreIdRmaListSortKeyIndex";
    static final String TABLE = "RMA";
    static final String ATTRIBUTE = "rmaListSortKey";
    static final Duration TIMEOUT = Duration.ofMinutes(10);
    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final AmazonDynamoDB dynamoDB;
    private final LongConsumer pause;
    private final Duration timeout;

    @ChangeUnitConstructor
    public V021_AddRmaListSortKeyIndex(AmazonDynamoDB dynamoDB) {
        this(dynamoDB, V021_AddRmaListSortKeyIndex::sleep, TIMEOUT);
    }

    V021_AddRmaListSortKeyIndex(AmazonDynamoDB dynamoDB, LongConsumer pause, Duration timeout) {
        this.dynamoDB = dynamoDB;
        this.pause = pause;
        this.timeout = timeout;
    }

    @Execution
    public void execute() {
        backfillRmaListSortKeys();
        createIndexIfAbsent();
    }

    private void createIndexIfAbsent() {
        if (hasIndex(describe())) {
            return;
        }
        awaitUntil(table -> busyIndexes(table).isEmpty() && isActive(table.getTableStatus()),
                "the index operations on RMA to finish");
        TableDescription table = describe();
        CreateGlobalSecondaryIndexAction create = new CreateGlobalSecondaryIndexAction()
                .withIndexName(INDEX)
                .withKeySchema(new KeySchemaElement("storeId", KeyType.HASH), new KeySchemaElement(ATTRIBUTE, KeyType.RANGE))
                // the list shows the client, shipments and value of every row, so the index carries whole RMAs
                .withProjection(new Projection().withProjectionType(ProjectionType.ALL));
        // A provisioned table rejects an index without its own throughput (same as V018-V020).
        boolean onDemand = table.getBillingModeSummary() != null
                && BillingMode.PAY_PER_REQUEST.toString().equals(table.getBillingModeSummary().getBillingMode());
        if (!onDemand) {
            ProvisionedThroughputDescription capacity = table.getProvisionedThroughput();
            create.withProvisionedThroughput(new ProvisionedThroughput(
                    Math.max(1L, capacity.getReadCapacityUnits()), Math.max(1L, capacity.getWriteCapacityUnits())));
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName(TABLE)
                .withAttributeDefinitions(
                        new AttributeDefinition("storeId", ScalarAttributeType.S),
                        new AttributeDefinition(ATTRIBUTE, ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate().withCreate(create)));
    }

    /**
     * Scans page by page with a narrow projection (a mapper scan list caches every item it iterates). An item that
     * already carries its current key is skipped, so a resumed run only writes what is missing.
     */
    private void backfillRmaListSortKeys() {
        DynamoDBMapper mapper = new DynamoDBMapper(dynamoDB);
        Map<String, AttributeValue> startKey = null;
        do {
            ScanResult page = dynamoDB.scan(new ScanRequest()
                    .withTableName(TABLE)
                    .withProjectionExpression("storeId, rmaId, #s, createdAt, " + ATTRIBUTE + ", #v")
                    .withExpressionAttributeNames(Map.of("#s", "status", "#v", "version"))
                    .withExclusiveStartKey(startKey));
            for (Map<String, AttributeValue> item : page.getItems()) {
                RMA scanned = mapper.marshallIntoObject(RMA.class, item);
                AttributeValue stored = item.get(ATTRIBUTE);
                if (stored != null && RmaListSortKey.of(scanned).equals(stored.getS())) {
                    continue;
                }
                backfillRmaListSortKey(scanned, item.containsKey("version"));
            }
            startKey = page.getLastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
    }

    /**
     * Writes the key computed from the scanned copy only while the RMA is still at the version the scan saw: a save in
     * between already wrote a fresh key, which a stale one must not overwrite. The version itself is never modified.
     */
    public void backfillRmaListSortKey(RMA scanned, boolean versioned) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":key", new AttributeValue(RmaListSortKey.of(scanned)));
        String condition = "attribute_exists(rmaId) AND attribute_not_exists(#v)";
        if (versioned) {
            condition = "attribute_exists(rmaId) AND #v = :v";
            values.put(":v", new AttributeValue().withN(String.valueOf(scanned.getVersion())));
        }
        try {
            dynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(TABLE)
                    .withKey(Map.of("storeId", new AttributeValue(scanned.getStoreId()),
                            "rmaId", new AttributeValue(scanned.getRmaId())))
                    .withUpdateExpression("SET " + ATTRIBUTE + " = :key")
                    .withConditionExpression(condition)
                    .withExpressionAttributeNames(Map.of("#v", "version"))
                    .withExpressionAttributeValues(values));
        } catch (ConditionalCheckFailedException changedMeanwhile) {
            // deleted or saved between the scan and the update: a save already wrote the current key
        }
    }

    private TableDescription describe() {
        return dynamoDB.describeTable(TABLE).getTable();
    }

    private static boolean hasIndex(TableDescription table) {
        return table.getGlobalSecondaryIndexes() != null
                && table.getGlobalSecondaryIndexes().stream().anyMatch(index -> INDEX.equals(index.getIndexName()));
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

    private void awaitUntil(Predicate<TableDescription> done, String what) {
        long waitedMillis = 0;
        while (!done.test(describe())) {
            if (waitedMillis >= timeout.toMillis()) {
                TableDescription table = describe();
                throw new IllegalStateException("V021: timed out after " + timeout + " waiting for " + what
                        + " on RMA; table status " + table.getTableStatus() + ", busy indexes " + busyIndexes(table)
                        + ". Restart the application to resume V021.");
            }
            pause.accept(POLL_INTERVAL.toMillis());
            waitedMillis += POLL_INTERVAL.toMillis();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("V021 interrupted while waiting for the RMA table", e);
        }
    }

    @RollbackExecution
    public void rollback() {
        // No-op: after a client-side timeout the index may still be CREATING, and deleting it would lose the build.
    }
}
