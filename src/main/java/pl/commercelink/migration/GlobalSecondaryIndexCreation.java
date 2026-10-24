package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateGlobalSecondaryIndexAction;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexUpdate;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Adds a global secondary index with two string keys to an existing table. DynamoDB runs one index create or delete
 * per table at a time, so it first waits, bounded by the timeout, for the table to be idle. It does not wait for the
 * new index to become ACTIVE: DynamoDB backfills it from the items already in the table.
 */
@RequiredArgsConstructor
final class GlobalSecondaryIndexCreation {

    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final AmazonDynamoDB dynamoDB;
    private final String migration;
    private final LongConsumer pause;
    private final Duration timeout;

    /** False when the table already has the index, whatever its keys and state. */
    boolean createIfAbsent(String table, String index, String hashKey, String rangeKey, Projection projection) {
        if (hasIndex(describe(table), index)) {
            return false;
        }
        awaitIdle(table);
        TableDescription description = describe(table);
        CreateGlobalSecondaryIndexAction create = new CreateGlobalSecondaryIndexAction()
                .withIndexName(index)
                .withKeySchema(new KeySchemaElement(hashKey, KeyType.HASH), new KeySchemaElement(rangeKey, KeyType.RANGE))
                .withProjection(projection);
        // A table on provisioned capacity rejects an index without its own throughput (and a failed migration stops the
        // application from starting); tables created by hand may be provisioned, so the index takes the table's figures.
        boolean onDemand = description.getBillingModeSummary() != null
                && BillingMode.PAY_PER_REQUEST.toString().equals(description.getBillingModeSummary().getBillingMode());
        if (!onDemand) {
            ProvisionedThroughputDescription capacity = description.getProvisionedThroughput();
            create.withProvisionedThroughput(new ProvisionedThroughput(
                    Math.max(1L, capacity.getReadCapacityUnits()), Math.max(1L, capacity.getWriteCapacityUnits())));
        }
        dynamoDB.updateTable(new UpdateTableRequest()
                .withTableName(table)
                .withAttributeDefinitions(
                        new AttributeDefinition(hashKey, ScalarAttributeType.S),
                        new AttributeDefinition(rangeKey, ScalarAttributeType.S))
                .withGlobalSecondaryIndexUpdates(new GlobalSecondaryIndexUpdate().withCreate(create)));
        return true;
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a DynamoDB table", e);
        }
    }

    private void awaitIdle(String table) {
        long waitedMillis = 0;
        while (!isIdle(describe(table))) {
            if (waitedMillis >= timeout.toMillis()) {
                TableDescription description = describe(table);
                throw new IllegalStateException(migration + ": timed out after " + timeout
                        + " waiting for the index operations on " + table + " to finish; table status "
                        + description.getTableStatus() + ", busy indexes " + busyIndexes(description)
                        + ". Restart the application to resume " + migration + ".");
            }
            pause.accept(POLL_INTERVAL.toMillis());
            waitedMillis += POLL_INTERVAL.toMillis();
        }
    }

    private TableDescription describe(String table) {
        return dynamoDB.describeTable(table).getTable();
    }

    private static boolean hasIndex(TableDescription table, String index) {
        return table.getGlobalSecondaryIndexes() != null
                && table.getGlobalSecondaryIndexes().stream().anyMatch(existing -> index.equals(existing.getIndexName()));
    }

    private static boolean isIdle(TableDescription table) {
        return busyIndexes(table).isEmpty() && isActive(table.getTableStatus());
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
}
