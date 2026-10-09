package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.QueryPageResult;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class OrdersRepository extends DynamoDbRepository<Order> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrdersRepository.class);
    static final String STORE_STATUS_INDEX = "StoreIdStatusIndex";

    public OrdersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Order findById(String storeId, String orderId) {
        return dynamoDBMapper.load(Order.class, storeId, orderId);
    }

    /** The store's orders with these ids, read in batches of 100 keys; ids not in the store are absent. */
    public List<Order> findAllByIds(String storeId, Collection<String> orderIds) {
        List<Order> keys = orderIds.stream().distinct().map(id -> {
            Order key = new Order();
            key.setStoreId(storeId);
            key.setOrderId(id);
            return key;
        }).toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        return dynamoDBMapper.batchLoad(keys).values().stream().flatMap(List::stream).map(Order.class::cast).toList();
    }

    public List<Order> findAll(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Order.class, scanExpression);
    }

    /**
     * Every order of a store, newest first. A query on the table's own partition key (storeId) returns whole items;
     * StoreIdOrderedAtIndex would not — its projection carries only the id, status, e-mail and fulfilment type, which
     * is enough for the fulfilment queue but not for the orders list (spec §8.3, D7).
     */
    public List<Order> findByStore(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        DynamoDBQueryExpression<Order> queryExpression = new DynamoDBQueryExpression<Order>()
                .withKeyConditionExpression("storeId = :storeId")
                .withExpressionAttributeValues(eav);

        // PaginatedQueryList loads lazily; copying it walks every page before the list sorts and filters.
        List<Order> orders = new ArrayList<>(dynamoDBMapper.query(Order.class, queryExpression));
        orders.sort(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return orders;
    }

    /**
     * The store's orders in the given statuses, read through StoreIdStatusIndex: one query per status (every page
     * followed) for the keys, then a batch load of the orders themselves — so a store's growing history of Completed
     * and Cancelled orders is never read when only open ones are asked for. The index is eventually consistent, so an
     * order whose status changed a moment ago is dropped when its loaded status is no longer asked for. Until the index
     * exists and is active (it is built in the background after V018 runs) the store's partition is read instead.
     */
    public List<Order> findByStoreAndStatuses(String storeId, Collection<OrderStatus> statuses) {
        Set<OrderStatus> wanted = statuses.isEmpty() ? EnumSet.noneOf(OrderStatus.class) : EnumSet.copyOf(statuses);
        List<Order> keys = new ArrayList<>();
        try {
            for (OrderStatus status : wanted) {
                Map<String, AttributeValue> startKey = null;
                do {
                    QueryResult page = amazonDynamoDB.query(new QueryRequest()
                            .withTableName("Orders")
                            .withIndexName(STORE_STATUS_INDEX)
                            .withKeyConditionExpression("storeId = :storeId AND #status = :status")
                            .withExpressionAttributeNames(Map.of("#status", "status"))
                            .withExpressionAttributeValues(Map.of(
                                    ":storeId", new AttributeValue().withS(storeId),
                                    ":status", new AttributeValue().withS(status.name())))
                            .withExclusiveStartKey(startKey));
                    page.getItems().forEach(item -> keys.add(orderKey(storeId, item.get("orderId").getS())));
                    startKey = page.getLastEvaluatedKey();
                } while (startKey != null && !startKey.isEmpty());
            }
        } catch (AmazonDynamoDBException e) {
            if (!indexUnavailable(e)) {
                throw e;
            }
            log.warn("{} is not available yet ({}); reading the store's partition for the orders list", STORE_STATUS_INDEX, e.getErrorMessage());
            return findByStore(storeId).stream().filter(order -> wanted.contains(order.getStatus())).toList();
        }
        if (keys.isEmpty()) {
            return new ArrayList<>();
        }
        List<Order> orders = dynamoDBMapper.batchLoad(keys).values().stream()
                .flatMap(List::stream)
                .map(Order.class::cast)
                .filter(order -> wanted.contains(order.getStatus()))
                .sorted(Comparator.comparing(Order::getOrderedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toCollection(ArrayList::new));
        return orders;
    }

    /**
     * The given orders of one store, in the order the ids were given, read in one batch (BatchGetItem; the mapper splits
     * it into calls of 100 keys and retries unprocessed ones). An id that names no order of this store is left out and an
     * id given twice counts once. The store is part of every key, so another store's order is never read; the filter
     * below only guards against a batch answering with anything else.
     */
    public List<Order> findByIds(String storeId, List<String> orderIds) {
        List<String> ids = orderIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Order> loaded = new HashMap<>();
        dynamoDBMapper.batchLoad(ids.stream().map(id -> orderKey(storeId, id)).toList()).values().stream()
                .flatMap(List::stream)
                .map(Order.class::cast)
                .filter(order -> storeId.equals(order.getStoreId()))
                .forEach(order -> loaded.put(order.getOrderId(), order));
        return ids.stream().map(loaded::get).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * A missing index ("The table does not have the specified index: StoreIdStatusIndex") or one still being built after
     * V018 ("Cannot read from backfilling global secondary index: StoreIdStatusIndex") answers with an error naming it;
     * every other error is a real failure and is not hidden behind the fallback.
     */
    private static boolean indexUnavailable(AmazonDynamoDBException e) {
        return e.getErrorMessage() != null && e.getErrorMessage().contains(STORE_STATUS_INDEX);
    }

    private static Order orderKey(String storeId, String orderId) {
        Order key = new Order();
        key.setStoreId(storeId);
        key.setOrderId(orderId);
        return key;
    }

    public List<Order> findByShortId(String storeId, String prefix) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":prefix", new AttributeValue().withS(prefix));
        return queryAll(() -> new QueryRequest()
                .withTableName("Orders")
                .withKeyConditionExpression("storeId = :storeId AND begins_with(orderId, :prefix)")
                .withExpressionAttributeValues(eav));
    }

    // A raw QueryRequest stops at 1 MB and does not page on its own (DynamoDBMapper.scan did), so every page is
    // followed explicitly; a fresh request per page keeps each call's start key its own.
    private List<Order> queryAll(Supplier<QueryRequest> request) {
        List<Order> orders = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        do {
            QueryRequest page = request.get();
            if (startKey != null && !startKey.isEmpty()) {
                page.withExclusiveStartKey(startKey);
            }
            QueryResult result = amazonDynamoDB.query(page);
            result.getItems().stream()
                    .map(item -> dynamoDBMapper.marshallIntoObject(Order.class, item))
                    .forEach(orders::add);
            startKey = result.getLastEvaluatedKey();
        } while (startKey != null && !startKey.isEmpty());
        return orders;
    }

    /** Hits of ExternalOrderIdIndex: the index projects only the keys and orderId, so every other field is null. */
    public List<Order> findAllByStoreIdAndExternalOrderId(String storeId, String externalOrderId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":externalOrderId", new AttributeValue().withS(externalOrderId));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName("Orders")
                .withIndexName("ExternalOrderIdIndex")
                .withKeyConditionExpression("storeId = :storeId AND externalOrderId = :externalOrderId")
                .withExpressionAttributeValues(eav);

        return query(queryRequest, Order.class);
    }

    public Order findByStoreIdAndExternalOrderId(String storeId, String externalOrderId) {
        List<Order> orders = findAllByStoreIdAndExternalOrderId(storeId, externalOrderId);
        return orders.isEmpty() ? null : orders.get(0);
    }

    /**
     * This query is a filter over the store's whole Orders partition (splitFromOrderId is not a key), so it
     * is expensive relative to a keyed lookup. Callers should call it only when needed (e.g. on a miss
     * against the parent's own data), and it fetches only the attributes those callers actually use.
     */
    public List<Order> findBySplitFromOrderId(String storeId, String orderId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":splitFromOrderId", new AttributeValue().withS(orderId));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");

        DynamoDBQueryExpression<Order> queryExpression = new DynamoDBQueryExpression<Order>()
                .withKeyConditionExpression("storeId = :storeId")
                .withFilterExpression("splitFromOrderId = :splitFromOrderId")
                .withExpressionAttributeValues(eav)
                .withProjectionExpression("storeId, orderId, #status")
                .withExpressionAttributeNames(expressionAttributeNames);

        return dynamoDBMapper.query(Order.class, queryExpression);
    }

    public List<Order> findAllByStoreIdAndStatus(String storeId, OrderStatus... statuses) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        StringBuilder filterExpression = new StringBuilder("storeId = :storeId AND (");
        for (int i = 0; i < statuses.length; i++) {
            if (i > 0) {
                filterExpression.append(" OR ");
            }
            filterExpression.append("#status").append(i).append(" = :status").append(i);
            eav.put(":status" + i, new AttributeValue().withS(statuses[i].name()));
        }
        filterExpression.append(")");

        Map<String, String> expressionAttributeNames = new HashMap<>();
        for (int i = 0; i < statuses.length; i++) {
            expressionAttributeNames.put("#status" + i, "status");
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(expressionAttributeNames);

        return dynamoDBMapper.scan(Order.class, scanExpression);
    }

    public List<OrderIndexEntry> findAllWarehouseFulfilmentOrder(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":statusNew", new AttributeValue().withS(OrderStatus.New.name()));
        eav.put(":warehouseFulfilment", new AttributeValue().withS(FulfilmentType.WarehouseFulfilment.name()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");

        QueryRequest warehouseFulfilmentQuery = new QueryRequest()
                .withTableName("Orders")
                .withIndexName("StoreIdOrderedAtIndex")
                .withKeyConditionExpression("storeId = :storeId")
                .withFilterExpression("#status = :statusNew AND fulfilmentType = :warehouseFulfilment")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(eav)
                .withScanIndexForward(true);

        QueryResult queryResult = amazonDynamoDB.query(warehouseFulfilmentQuery);
        return queryResult.getItems().stream()
                .map(this::toOrderIndexEntry)
                .collect(Collectors.toList());
    }

    public QueryPageResult<OrderIndexEntry> findOldestOrdersWaitingForFulfilment(String storeId, List<String> excludedOrderIds, int limit, Map<String, AttributeValue> exclusiveStartKey) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":statusNew", new AttributeValue().withS(OrderStatus.New.name()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");

        String notInClause = "";
        if (excludedOrderIds != null && !excludedOrderIds.isEmpty()) {
            List<String> orderIdPlaceholders = new ArrayList<>();
            for (int i = 0; i < excludedOrderIds.size(); i++) {
                String placeholder = ":excluded" + i;
                orderIdPlaceholders.add(placeholder);
                eav.put(placeholder, new AttributeValue().withS(excludedOrderIds.get(i)));
            }
            notInClause = " AND NOT (orderId IN (" + String.join(", ", orderIdPlaceholders) + "))";
        }
        String filterExpression = "#status = :statusNew" + notInClause;

        QueryRequest query = new QueryRequest()
                .withTableName("Orders")
                .withIndexName("StoreIdOrderedAtIndex")
                .withKeyConditionExpression("storeId = :storeId")
                .withFilterExpression(filterExpression)
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(eav)
                .withScanIndexForward(true)
                .withLimit(limit);

        if (exclusiveStartKey != null) {
            query.withExclusiveStartKey(exclusiveStartKey);
        }

        QueryResult result = amazonDynamoDB.query(query);
        List<OrderIndexEntry> orders = result.getItems().stream()
                .map(this::toOrderIndexEntry)
                .collect(Collectors.toList());

        return new QueryPageResult<>(orders, result.getLastEvaluatedKey());
    }

    private static final String ORDERED_AT_INDEX = "StoreIdOrderedAtIndex";

    /**
     * The store's orders from StoreIdOrderedAtIndex in order-day order, read lazily: the next DynamoDB page is asked for
     * only when the previous one is used up, so a caller that stops after 26 entries pays for one call. The days are
     * compared as text: "from" as the bare date sorts before every time of that day (and equals a legacy date-only value),
     * "to" is exclusive on the next day, so the last fraction of a second of the "to" day is still in.
     */
    public Iterator<ArchiveIndexEntry> archiveIndex(String storeId, ArchiveIndexRequest request) {
        Map<String, AttributeValue> values = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        values.put(":storeId", new AttributeValue().withS(storeId));
        names.put("#status", "status");
        String key = "storeId = :storeId";
        if (request.from() != null || request.to() != null) {
            // DynamoDB rejects a name placeholder that no expression uses
            names.put("#orderedAt", "orderedAt");
        }
        if (request.from() != null && request.to() != null) {
            key += " AND #orderedAt BETWEEN :from AND :toEnd";
        } else if (request.from() != null) {
            key += " AND #orderedAt >= :from";
        } else if (request.to() != null) {
            key += " AND #orderedAt < :toNext";
        }
        if (request.from() != null) {
            values.put(":from", new AttributeValue().withS(request.from().toString()));
        }
        if (request.to() != null && request.from() != null) {
            // a key condition takes one comparison of the range key, so with both days it is BETWEEN (inclusive): the
            // upper bound is the highest text of the "to" day, above every "yyyy-MM-ddT…" of it and below the next day
            values.put(":toEnd", new AttributeValue().withS(request.to() + "T￿"));
        } else if (request.to() != null) {
            values.put(":toNext", new AttributeValue().withS(request.to().plusDays(1).toString()));
        }
        List<String> statusPlaceholders = new ArrayList<>();
        int i = 0;
        for (OrderStatus status : request.statuses()) {
            String placeholder = ":s" + i++;
            statusPlaceholders.add(placeholder);
            values.put(placeholder, new AttributeValue().withS(status.name()));
        }
        String filter = "#status IN (" + String.join(", ", statusPlaceholders) + ")";
        String keyCondition = key;
        Map<String, AttributeValue> firstStart = request.start() == null ? null : Map.of(
                "storeId", new AttributeValue().withS(storeId),
                "orderId", new AttributeValue().withS(request.start().orderId()),
                "orderedAt", new AttributeValue().withS(request.start().orderedAt()));

        return new Iterator<>() {
            private final Deque<ArchiveIndexEntry> buffer = new ArrayDeque<>();
            private Map<String, AttributeValue> startKey = firstStart;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                while (buffer.isEmpty() && !exhausted) {
                    QueryRequest query = new QueryRequest()
                            .withTableName("Orders")
                            .withIndexName(ORDERED_AT_INDEX)
                            .withKeyConditionExpression(keyCondition)
                            .withFilterExpression(filter)
                            .withExpressionAttributeValues(values)
                            .withExpressionAttributeNames(names)
                            .withScanIndexForward(!request.newestFirst())
                            .withLimit(request.pageLimit());
                    if (startKey != null) {
                        query.withExclusiveStartKey(startKey);
                    }
                    QueryResult result = amazonDynamoDB.query(query);
                    result.getItems().forEach(item -> buffer.add(new ArchiveIndexEntry(
                            item.get("orderId").getS(),
                            item.get("orderedAt").getS(),
                            OrderStatus.valueOf(item.get("status").getS()),
                            item.containsKey("email") ? item.get("email").getS() : null)));
                    startKey = result.getLastEvaluatedKey();
                    exhausted = startKey == null || startKey.isEmpty();
                }
                return !buffer.isEmpty();
            }

            @Override
            public ArchiveIndexEntry next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return buffer.poll();
            }
        };
    }


    public List<Order> findAllOrders(String storeId, LocalDate dateFrom, LocalDate dateTo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":dateFrom", new AttributeValue().withS(dateFrom.atStartOfDay().toString()));
        eav.put(":dateTo", new AttributeValue().withS(dateTo.atTime(23, 59, 59).toString()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#orderedAt", "orderedAt");

        QueryRequest queryRequest = new QueryRequest()
                .withTableName("Orders")
                .withIndexName("StoreIdOrderedAtIndex")
                .withKeyConditionExpression("storeId = :storeId AND #orderedAt BETWEEN :dateFrom AND :dateTo")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(expressionAttributeNames);

        QueryResult queryResult = amazonDynamoDB.query(queryRequest);

        return queryResult.getItems().stream()
                .map(item -> dynamoDBMapper.load(Order.class, item.get("storeId").getS(), item.get("orderId").getS()))
                .collect(Collectors.toList());
    }

    public List<OrderIndexEntry> findAllPastOrders(LocalDate dateFrom, LocalDate dateTo, String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":dateFrom", new AttributeValue().withS(dateFrom.atStartOfDay().toString()));
        eav.put(":dateTo", new AttributeValue().withS(dateTo.atTime(23, 59, 59).toString()));
        eav.put(":statusNew", new AttributeValue().withS(OrderStatus.New.name()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");
        expressionAttributeNames.put("#orderedAt", "orderedAt");

        QueryRequest queryRequest = new QueryRequest()
            .withTableName("Orders")
            .withIndexName("StoreIdOrderedAtIndex")
            .withKeyConditionExpression("storeId = :storeId AND #orderedAt BETWEEN :dateFrom AND :dateTo")
            .withFilterExpression("#status <> :statusNew")
            .withExpressionAttributeValues(eav)
            .withExpressionAttributeNames(expressionAttributeNames);

        QueryResult queryResult = amazonDynamoDB.query(queryRequest);

        return queryResult.getItems().stream()
                .map(this::toOrderIndexEntry)
                .collect(Collectors.toList());
    }

    public List<Order> findByEmail(String storeId, String email) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":email", new AttributeValue().withS(email));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId AND email = :email")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Order.class, scanExpression);
    }

    private LocalDateTime parseOrderedAt(String orderedAtString) {
        if (orderedAtString.length() < 12) {
            return LocalDateTime.of(LocalDate.parse(orderedAtString), LocalTime.NOON);
        }
        return LocalDateTime.parse(orderedAtString);
    }

    private OrderIndexEntry toOrderIndexEntry(Map<String, AttributeValue> item) {
        return new OrderIndexEntry(
                item.get("storeId").getS(),
                item.get("orderId").getS(),
                item.get("email").getS(),
                parseOrderedAt(item.get("orderedAt").getS()),
                OrderStatus.valueOf(item.get("status").getS()),
                FulfilmentType.valueOf(item.get("fulfilmentType").getS())
        );
    }
}