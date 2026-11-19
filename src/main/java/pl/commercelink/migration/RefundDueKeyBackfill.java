package pl.commercelink.migration;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import lombok.extern.slf4j.Slf4j;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Brings refundDueSortKey of the recently cancelled orders in line with the order itself (RefundDueSortKey): set where
 * the order awaits a refund, removed where it no longer does. V029 runs it before creating StoreIdRefundDueIndex. It is
 * a class of its own, and idempotent, so that a later change unit can run it again: that is what picks up an order
 * cancelled by an application version that does not write the key (the old instance during a rolling deploy, or any
 * instance after a rollback), which would otherwise stay off the orders list until its next save.
 * <p>
 * Only orders placed in the last WINDOW_DAYS days are looked at, the window the list read before the index: older
 * cancelled orders whose refund was never recorded would otherwise all surface on the list at once.
 */
@Slf4j
final class RefundDueKeyBackfill {

    static final String ATTRIBUTE = "refundDueSortKey";
    // a literal, not OrdersRepository's constant: an applied change unit must keep doing what it did
    static final int WINDOW_DAYS = 90;
    private static final int ATTEMPTS = 3;

    private final AmazonDynamoDB dynamoDB;
    private final DynamoDBMapper mapper;

    RefundDueKeyBackfill(AmazonDynamoDB dynamoDB) {
        this.dynamoDB = dynamoDB;
        this.mapper = new DynamoDBMapper(dynamoDB);
    }

    /**
     * Scans StoreIdOrderedAtIndex, not the table: its entries are small and carry the status, so only the cancelled
     * orders of the window are then read in full.
     */
    void run(LocalDate today) {
        Map<String, AttributeValue> values = Map.of(
                ":cancelled", new AttributeValue(OrderStatus.Cancelled.name()),
                ":cutoff", new AttributeValue(today.minusDays(WINDOW_DAYS).atStartOfDay().toString()));
        Map<String, AttributeValue> startKey = null;
        do {
            ScanResult page = dynamoDB.scan(new ScanRequest()
                    .withTableName("Orders")
                    .withIndexName("StoreIdOrderedAtIndex")
                    .withFilterExpression("#status = :cancelled AND orderedAt >= :cutoff")
                    .withExpressionAttributeNames(Map.of("#status", "status"))
                    .withExpressionAttributeValues(values)
                    .withExclusiveStartKey(startKey));
            page.getItems().forEach(entry -> align(entry.get("storeId").getS(), entry.get("orderId").getS()));
            startKey = page.getLastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
    }

    /**
     * A write refused because the order changed is tried again from a fresh read instead of being skipped: the save
     * that got in between may come from an instance that does not write the key.
     */
    private void align(String storeId, String orderId) {
        Map<String, AttributeValue> key = Map.of("storeId", new AttributeValue(storeId), "orderId", new AttributeValue(orderId));
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Map<String, AttributeValue> item = dynamoDB.getItem(new GetItemRequest()
                    .withTableName("Orders")
                    .withKey(key)
                    .withConsistentRead(true)).getItem();
            if (item == null) {
                return;
            }
            String wanted;
            try {
                wanted = mapper.marshallIntoObject(Order.class, item).getRefundDueSortKey();
            } catch (RuntimeException unreadable) {
                // nothing in here talks to DynamoDB, so it is the record itself: a single order the mapper cannot read
                // must not keep the application of every store from starting
                log.error("Order {} of store {} cannot be read and is skipped by the refund key backfill: it may be "
                        + "missing from the orders awaiting a refund", orderId, storeId, unreadable);
                return;
            }
            String stored = item.containsKey(ATTRIBUTE) ? item.get(ATTRIBUTE).getS() : null;
            if (Objects.equals(wanted, stored) || write(key, item.get("version"), wanted)) {
                return;
            }
        }
        log.error("Order {} of store {} kept changing during the refund key backfill: it may be missing from the orders "
                + "awaiting a refund until its next save", orderId, storeId);
    }

    /** Guarded on the version the read saw, which itself is never modified; false when the order changed since. */
    private boolean write(Map<String, AttributeValue> key, AttributeValue version, String wanted) {
        Map<String, String> names = new HashMap<>(Map.of("#key", ATTRIBUTE, "#v", "version"));
        Map<String, AttributeValue> values = new HashMap<>();
        String condition = "attribute_exists(orderId) AND attribute_not_exists(#v)";
        if (version != null) {
            condition = "attribute_exists(orderId) AND #v = :v";
            values.put(":v", version);
        }
        String update = "REMOVE #key";
        if (wanted != null) {
            update = "SET #key = :key";
            values.put(":key", new AttributeValue(wanted));
        }
        try {
            dynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName("Orders")
                    .withKey(key)
                    .withUpdateExpression(update)
                    .withConditionExpression(condition)
                    .withExpressionAttributeNames(names)
                    // DynamoDB rejects an empty map of values
                    .withExpressionAttributeValues(values.isEmpty() ? null : values));
            return true;
        } catch (ConditionalCheckFailedException changedMeanwhile) {
            return false;
        }
    }
}
