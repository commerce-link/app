package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;
import pl.commercelink.stores.ConnectionMode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@Component
public class  DeliveriesRepository extends DynamoDbRepository<Delivery> {

    public DeliveriesRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Delivery findById(String storeId, String deliveryId) {
        return dynamoDBMapper.load(Delivery.class, storeId, deliveryId);
    }

    /**
     * For callers that read a delivery back in the same request that wrote it - an eventually consistent
     * read can still answer with the replica from before the write, and a delivery that reads as missing
     * silently changes a decision (see DropshipItemLookup).
     */
    public Delivery findByIdConsistently(String storeId, String deliveryId) {
        return dynamoDBMapper.load(Delivery.class, storeId, deliveryId, DynamoDBMapperConfig.builder()
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .build());
    }

    public Optional<Delivery> findByExternalDeliveryId(String storeId, String externalDeliveryId) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":externalDeliveryId", new AttributeValue().withS(externalDeliveryId));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("externalDeliveryId = :externalDeliveryId")
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression)
                .stream()
                .findFirst();
    }

    public Optional<Delivery> findByPurchaseRef(String storeId, String purchaseRef) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":purchaseRef", new AttributeValue().withS(purchaseRef));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("purchaseRef = :purchaseRef")
                .withExpressionAttributeValues(expressionAttributeValues)
                .withConsistentRead(true);

        return dynamoDBMapper.query(Delivery.class, queryExpression)
                .stream()
                .findFirst();
    }

    public List<Delivery> findAll(String storeId, LocalDateTime from, LocalDateTime to) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":from", new AttributeValue().withS(from.toString()));
        expressionAttributeValues.put(":to", new AttributeValue().withS(to.toString()));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("orderedAt BETWEEN :from AND :to")
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression);
    }

    public List<Delivery> findUnpaidDeliveries(String storeId) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":true", new AttributeValue().withN("1"));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("attribute_not_exists(paid) OR paid <> :true")
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression)
                .stream()
                .sorted(Comparator.comparing(Delivery::getPaymentDueDate))
                .collect(Collectors.toList());
    }

    public static final String LIST_INDEX = "StoreIdListKeyIndex";

    /**
     * The store's deliveries on their way (spec §7.2). Like every list read below it answers with partial deliveries -
     * only the attributes StoreIdListKeyIndex carries - which must never be saved back.
     */
    public List<Delivery> findInTransit(String storeId) {
        return readList(storeId, "begins_with(listKey, :lo)", DeliveryListKey.IN_TRANSIT, null,
                key -> key.startsWith(DeliveryListKey.IN_TRANSIT));
    }

    /** Received deliveries still waiting for a purchase invoice, over the whole history. Partial, see findInTransit. */
    public List<Delivery> findToSettle(String storeId) {
        return readList(storeId, "begins_with(listKey, :lo)", DeliveryListKey.TO_SETTLE, null,
                key -> key.startsWith(DeliveryListKey.TO_SETTLE));
    }

    /**
     * Received deliveries in a reception window; a missing bound means from the start / up to now. Partial, see
     * findInTransit.
     */
    public List<Delivery> findReceivedBetween(String storeId, LocalDate from, LocalDate to) {
        List<Delivery> result = new ArrayList<>();
        for (String prefix : List.of(DeliveryListKey.TO_SETTLE, DeliveryListKey.SETTLED)) {
            String lo = from == null ? prefix : DeliveryListKey.receivedBound(prefix, from, false);
            String hi = to == null ? prefix + "\uffff" : DeliveryListKey.receivedBound(prefix, to, true);
            result.addAll(readList(storeId, "listKey BETWEEN :lo AND :hi", lo, hi,
                    key -> key.compareTo(lo) >= 0 && key.compareTo(hi) <= 0));
        }
        return result;
    }

    /** A delivery number typed from the list (its first 8 characters) or whole; the table key, no index needed. */
    public List<Delivery> findByDeliveryIdPrefix(String storeId, String prefix) {
        if (isBlank(prefix)) {
            return List.of();
        }
        Map<String, AttributeValue> eav = Map.of(":storeId", new AttributeValue(storeId), ":p", new AttributeValue(prefix));
        return new ArrayList<>(dynamoDBMapper.query(Delivery.class, new DynamoDBQueryExpression<Delivery>()
                .withKeyConditionExpression("storeId = :storeId AND begins_with(deliveryId, :p)")
                .withExpressionAttributeValues(eav)));
    }

    /**
     * One key range of StoreIdListKeyIndex, every page followed. The index is eventually consistent, so each delivery's
     * key is recomputed from the projected attributes and one that has already moved on is dropped (the same guard as
     * OrdersRepository.findByStoreAndStatuses). A missing index or one still being built after V019 names the index in
     * its error; the store's partition is read instead and keyed the same way.
     */
    private List<Delivery> readList(String storeId, String keyCondition, String lo, String hi, Predicate<String> keyMatches) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue(storeId));
        eav.put(":lo", new AttributeValue(lo));
        if (hi != null) {
            eav.put(":hi", new AttributeValue(hi));
        }
        List<Delivery> result = new ArrayList<>();
        try {
            Map<String, AttributeValue> startKey = null;
            do {
                QueryResult page = amazonDynamoDB.query(new QueryRequest()
                        .withTableName("Deliveries")
                        .withIndexName(LIST_INDEX)
                        .withKeyConditionExpression("storeId = :storeId AND " + keyCondition)
                        .withExpressionAttributeValues(eav)
                        .withExclusiveStartKey(startKey));
                page.getItems().forEach(item -> result.add(dynamoDBMapper.marshallIntoObject(Delivery.class, item)));
                startKey = page.getLastEvaluatedKey();
            } while (startKey != null && !startKey.isEmpty());
        } catch (AmazonDynamoDBException e) {
            if (e.getErrorMessage() == null || !e.getErrorMessage().contains(LIST_INDEX)) {
                throw e;
            }
            log.warn("{} is not available yet ({}); reading the store's partition for the deliveries list", LIST_INDEX, e.getErrorMessage());
            return findByStore(storeId).stream().filter(d -> keyMatches.test(DeliveryListKey.of(d))).toList();
        }
        return result.stream().filter(d -> keyMatches.test(DeliveryListKey.of(d))).toList();
    }

    private List<Delivery> findByStore(String storeId) {
        Delivery key = new Delivery();
        key.setStoreId(storeId);
        return new ArrayList<>(dynamoDBMapper.query(Delivery.class, new DynamoDBQueryExpression<Delivery>().withHashKeyValues(key)));
    }

    public List<Delivery> searchActiveDeliveries(String storeId, DeliveryFilter filter, int page, int pageSize) {
        QueryAndFilterExpressions expressions = buildFilterExpressions(filter, storeId);

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withKeyConditionExpression(expressions.keyConditionExpression)
                .withFilterExpression(expressions.filterExpression)
                .withExpressionAttributeValues(expressions.eav);

        return queryWithPagination(queryExpression, page, pageSize, Delivery.class)
                .stream()
                .sorted(Comparator.comparing(Delivery::getEstimatedDeliveryAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public List<Delivery> searchActiveDeliveries(DeliveryFilter filter, int page, int pageSize) {
        QueryAndFilterExpressions expressions = buildFilterExpressions(filter, null);

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(expressions.filterExpression)
                .withExpressionAttributeValues(expressions.eav);

        return scanWithPagination(scanExpression, page, pageSize, Delivery.class);
    }

    private QueryAndFilterExpressions buildFilterExpressions(DeliveryFilter filter, String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        StringBuilder filterExpression = new StringBuilder();
        String keyConditionExpression = null;

        eav.put(":null", new AttributeValue().withNULL(true));
        appendFilter(filterExpression, "(attribute_not_exists(deliveredAt) OR deliveredAt = :null)");

        if (storeId != null) {
            keyConditionExpression = "storeId = :storeId";
            eav.put(":storeId", new AttributeValue().withS(storeId));

            if (isNotBlank(filter.getDeliveryId())) {
                eav.put(":deliveryId", new AttributeValue().withS(filter.getDeliveryId()));
                keyConditionExpression += " AND deliveryId = :deliveryId";
            }
        } else {
            if (isNotBlank(filter.getDeliveryId())) {
                eav.put(":deliveryId", new AttributeValue().withS(filter.getDeliveryId()));
                appendFilter(filterExpression, "deliveryId = :deliveryId");
            }
        }

        if (isNotBlank(filter.getExternalDeliveryId())) {
            eav.put(":externalDeliveryId", new AttributeValue().withS(filter.getExternalDeliveryId()));
            appendFilter(filterExpression, "externalDeliveryId = :externalDeliveryId");
        }

        if (isNotBlank(filter.getProvider())) {
            eav.put(":provider", new AttributeValue().withS(filter.getProvider()));
            appendFilter(filterExpression, "provider = :provider");
        }

        // contains() keeps the match inside the DynamoDB filter, so pagination stays correct; it is case-sensitive.
        if (isNotBlank(filter.getCounterpartyShortcut())) {
            eav.put(":counterpartyShortcut", new AttributeValue().withS(filter.getCounterpartyShortcut()));
            appendFilter(filterExpression, "contains(counterpartyShortcut, :counterpartyShortcut)");
        }

        if (filter.getOrderedAtStart() != null && filter.getOrderedAtEnd() != null) {
            eav.put(":orderedAtStart", new AttributeValue().withS(filter.getOrderedAtStart().toString()));
            eav.put(":orderedAtEnd", new AttributeValue().withS(filter.getOrderedAtEnd().toString()));
            appendFilter(filterExpression, "orderedAt BETWEEN :orderedAtStart AND :orderedAtEnd");
        } else if (filter.getOrderedAtStart() != null) {
            eav.put(":orderedAtStart", new AttributeValue().withS(filter.getOrderedAtStart().toString()));
            appendFilter(filterExpression, "orderedAt >= :orderedAtStart");
        } else if (filter.getOrderedAtEnd() != null) {
            eav.put(":orderedAtEnd", new AttributeValue().withS(filter.getOrderedAtEnd().toString()));
            appendFilter(filterExpression, "orderedAt <= :orderedAtEnd");
        }

        if (filter.isWaitingForCollection()) {
            eav.put(":null", new AttributeValue().withNULL(true));
            appendFilter(filterExpression, "(attribute_not_exists(receivedAt) OR receivedAt = :null)");
        }

        if (filter.isWithoutInvoice()) {
            eav.put(":zero", new AttributeValue().withN("0"));
            appendFilter(filterExpression, "(attribute_not_exists(invoiced) OR invoiced = :zero)");
        }

        if (filter.isWithoutSync()) {
            eav.put(":false", new AttributeValue().withN("0"));
            appendFilter(filterExpression, "(attribute_not_exists(synced) OR synced = :false)");
        }

        if (filter.isAwaitingApproval()) {
            eav.put(":awaitingApproval", new AttributeValue().withS(DeliveryOrderStatus.AWAITING_APPROVAL.name()));
            appendFilter(filterExpression, "orderStatus = :awaitingApproval");
        }

        if (filter.isGlobalOnly()) {
            eav.put(":globalMode", new AttributeValue().withS(ConnectionMode.GLOBAL.name()));
            appendFilter(filterExpression, "connectionMode = :globalMode");
        }

        return new QueryAndFilterExpressions(keyConditionExpression, filterExpression.toString(), eav);
    }

    public List<Delivery> findPendingDeliveriesByProvider(String storeId, String provider, String excludedDeliveryId) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":null", new AttributeValue().withNULL(true));
        expressionAttributeValues.put(":provider", new AttributeValue().withS(provider));
        expressionAttributeValues.put(":dropship", new AttributeValue().withS(DeliveryType.DROPSHIP.name()));

        Map<String, String> expressionAttributeNames = Map.of("#type", "type");

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("provider = :provider AND (attribute_not_exists(receivedAt) OR receivedAt = :null)"
                        + " AND (attribute_not_exists(#type) OR #type <> :dropship)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression)
                .stream()
                .filter(d -> !d.getDeliveryId().equals(excludedDeliveryId))
                .sorted(Comparator.comparing(Delivery::getOrderedAt))
                .collect(Collectors.toList());
    }

    public List<Delivery> findTrackableDropshipDeliveries(String storeId) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":null", new AttributeValue().withNULL(true));
        expressionAttributeValues.put(":dropship", new AttributeValue().withS(DeliveryType.DROPSHIP.name()));
        expressionAttributeValues.put(":pending", new AttributeValue().withS(DeliveryTrackingState.PENDING.name()));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("#type = :dropship"
                        + " AND (attribute_not_exists(receivedAt) OR receivedAt = :null)"
                        + " AND (attribute_not_exists(orderStatus) OR orderStatus = :null)"
                        + " AND attribute_exists(externalDeliveryId)"
                        + " AND (attribute_not_exists(tracking) OR attribute_not_exists(tracking.#st) OR tracking.#st = :pending)")
                .withExpressionAttributeNames(Map.of("#type", "type", "#st", "state"))
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression)
                .stream()
                .filter(delivery -> isNotBlank(delivery.getExternalDeliveryId()))
                .sorted(Comparator.comparing(Delivery::getOrderedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private static class QueryAndFilterExpressions {
        private final String keyConditionExpression;
        private final String filterExpression;
        private final Map<String, AttributeValue> eav;

        QueryAndFilterExpressions(String keyConditionExpression, String filterExpression, Map<String, AttributeValue> eav) {
            this.keyConditionExpression = keyConditionExpression;
            this.filterExpression = filterExpression;
            this.eav = eav;
        }
    }
}
