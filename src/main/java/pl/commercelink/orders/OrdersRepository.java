package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.QueryPageResult;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class OrdersRepository extends DynamoDbRepository<Order> {

    public OrdersRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Order findById(String storeId, String orderId) {
        return dynamoDBMapper.load(Order.class, storeId, orderId);
    }

    public List<Order> findAll(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Order.class, scanExpression);
    }

    public Order findByStoreIdAndExternalOrderId(String storeId, String externalOrderId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":externalOrderId", new AttributeValue().withS(externalOrderId));

        QueryRequest queryRequest = new QueryRequest()
                .withTableName("Orders")
                .withIndexName("ExternalOrderIdIndex")
                .withKeyConditionExpression("storeId = :storeId AND externalOrderId = :externalOrderId")
                .withExpressionAttributeValues(eav);

        List<Order> orders = query(queryRequest, Order.class);
        return orders.isEmpty() ? null : orders.get(0);
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

    public List<Order> findAllActiveOrders(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":statusCompleted", new AttributeValue().withS(OrderStatus.Completed.name()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#status", "status");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId AND #status <> :statusCompleted")
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

    public List<OrderIndexEntry> searchPastOrders(String storeId, PastOrderFilter filter) {
        if (isNotBlank(filter.getOrderId())) {
            Order order = findById(storeId, filter.getOrderId());
            return order != null && order.getStatus() == OrderStatus.Completed
                    ? Collections.singletonList(OrderIndexEntry.fromOrder(order))
                    : Collections.emptyList();
        } else {
            Map<String, AttributeValue> eav = new HashMap<>();
            Map<String, String> expressionAttributeNames = new HashMap<>();
            StringBuilder filterExpression = new StringBuilder();

            eav.put(":storeId", new AttributeValue().withS(storeId));
            eav.put(":status", new AttributeValue().withS(OrderStatus.Completed.name()));
            expressionAttributeNames.put("#status", "status");
            appendFilter(filterExpression, "#status = :status");

            if (isNotBlank(filter.getEmail())) {
                eav.put(":email", new AttributeValue().withS(filter.getEmail()));
                appendFilter(filterExpression, "email = :email");
            }

            String keyCondition = "storeId = :storeId";
            LocalDate orderedAtStart = filter.getOrderedAtStart();
            LocalDate orderedAtEnd = filter.getOrderedAtEnd();
            if (orderedAtStart != null && orderedAtEnd != null) {
                keyCondition += " AND orderedAt BETWEEN :orderedAtStart AND :orderedAtEnd";
                eav.put(":orderedAtStart", new AttributeValue().withS(orderedAtStart.toString()));
                eav.put(":orderedAtEnd", new AttributeValue().withS(orderedAtEnd.toString()));
            } else if (orderedAtStart != null) {
                keyCondition += " AND orderedAt >= :orderedAtStart";
                eav.put(":orderedAtStart", new AttributeValue().withS(orderedAtStart.toString()));
            } else if (orderedAtEnd != null) {
                keyCondition += " AND orderedAt <= :orderedAtEnd";
                eav.put(":orderedAtEnd", new AttributeValue().withS(orderedAtEnd.toString()));
            }

            QueryRequest queryRequest = new QueryRequest()
                    .withTableName("Orders")
                    .withIndexName("StoreIdOrderedAtIndex")
                    .withKeyConditionExpression(keyCondition)
                    .withExpressionAttributeValues(eav)
                    .withExpressionAttributeNames(expressionAttributeNames)
                    .withFilterExpression(filterExpression.toString())
                    .withScanIndexForward(false);

            QueryResult queryResult = amazonDynamoDB.query(queryRequest);
            return queryResult.getItems().stream()
                    .map(this::toOrderIndexEntry)
                    .collect(Collectors.toList());
        }
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