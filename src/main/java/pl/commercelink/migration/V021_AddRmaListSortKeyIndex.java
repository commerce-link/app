package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.*;
import io.mongock.api.annotations.ChangeUnit;
import lombok.extern.slf4j.Slf4j;
import io.mongock.api.annotations.ChangeUnitConstructor;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import pl.commercelink.orders.rma.RMA;
import pl.commercelink.orders.rma.RmaListSortKey;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;

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
 * Waits are bounded; a timeout or any other failure is logged and the next start resumes (see execute). Once no
 * version older than this one can be deployed again, runAlways can be dropped in a later version.
 */
@Slf4j
@ChangeUnit(id = "V021-add-rma-list-sort-key-index", order = "021", author = "commercelink", runAlways = true)
public class V021_AddRmaListSortKeyIndex {

    public static final String INDEX = "StoreIdRmaListSortKeyIndex";
    static final String TABLE = "RMA";
    static final String ATTRIBUTE = "rmaListSortKey";
    static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final AmazonDynamoDB dynamoDB;
    private final GlobalSecondaryIndexCreation indexCreation;

    @ChangeUnitConstructor
    public V021_AddRmaListSortKeyIndex(AmazonDynamoDB dynamoDB) {
        this(dynamoDB, GlobalSecondaryIndexCreation::sleep, TIMEOUT);
    }

    V021_AddRmaListSortKeyIndex(AmazonDynamoDB dynamoDB, LongConsumer pause, Duration timeout) {
        this.dynamoDB = dynamoDB;
        this.indexCreation = new GlobalSecondaryIndexCreation(dynamoDB, "V021", pause, timeout);
    }

    /**
     * Neither step may stop the application: the unit runs on every start, so a failure that ended start-up would end
     * every later deploy too, while the list works without both (stale keys are dropped, a missing index means the
     * partition read). A failure is logged at ERROR and the next start does the work again.
     */
    @Execution
    public void execute() {
        try {
            backfillRmaListSortKeys();
        } catch (RuntimeException e) {
            log.error("V021: the RMA list keys were not backfilled, the next start tries again", e);
        }
        try {
            createIndexIfAbsent();
        } catch (RuntimeException e) {
            log.error("V021: {} was not created, the RMA list reads the store partitions until a start creates it", INDEX, e);
        }
    }

    private void createIndexIfAbsent() {
        // the list shows the client, shipments and value of every row, so the index carries whole RMAs
        indexCreation.createIfAbsent(TABLE, INDEX, "storeId", ATTRIBUTE,
                new Projection().withProjectionType(ProjectionType.ALL));
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
                try {
                    RMA scanned = mapper.marshallIntoObject(RMA.class, item);
                    AttributeValue stored = item.get(ATTRIBUTE);
                    if (stored != null && RmaListSortKey.of(scanned).equals(stored.getS())) {
                        continue;
                    }
                    backfillRmaListSortKey(scanned, item.containsKey("version"));
                } catch (RuntimeException e) {
                    // one malformed record (e.g. a status that is no enum value) must not fail the start-up of the application
                    log.error("V021: skipping RMA {} of store {}, its list key was not written: {}",
                            idOf(item, "rmaId"), idOf(item, "storeId"), e.getMessage());
                }
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

    private static String idOf(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null ? null : value.getS();
    }

    @RollbackExecution
    public void rollback() {
        // No-op: after a client-side timeout the index may still be CREATING, and deleting it would lose the build.
    }
}
