package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;
import pl.commercelink.orders.PaymentStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class  DeliveriesRepository extends DynamoDbRepository<Delivery> {

    public DeliveriesRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Delivery findById(String storeId, String deliveryId) {
        return dynamoDBMapper.load(Delivery.class, storeId, deliveryId);
    }

    public Delivery findByExternalDeliveryId(String storeId, String externalDeliveryId) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":storeId", new AttributeValue().withS(storeId));
        expressionAttributeValues.put(":externalDeliveryId", new AttributeValue().withS(externalDeliveryId));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withKeyConditionExpression("storeId = :storeId")
                .withFilterExpression("externalDeliveryId = :externalDeliveryId")
                .withExpressionAttributeValues(expressionAttributeValues)
                .withConsistentRead(false);

        List<Delivery> results = dynamoDBMapper.query(Delivery.class, queryExpression);
        return results.isEmpty() ? null : results.get(0);
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

    public List<Delivery> findAllActiveDeliveries(String storeId) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":paid", new AttributeValue().withS(PaymentStatus.Paid.toString()));
        expressionAttributeValues.put(":null", new AttributeValue().withNULL(true));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("paymentStatus <> :paid OR attribute_not_exists(receivedAt) OR receivedAt = :null")
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression);
    }

    public List<Delivery> searchActiveDeliveries(String storeId, DeliveryFilter filter, int page, int pageSize) {
        QueryAndFilterExpressions expressions = buildFilterExpressions(filter, storeId);

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withKeyConditionExpression(expressions.keyConditionExpression)
                .withFilterExpression(expressions.filterExpression)
                .withExpressionAttributeValues(expressions.eav);

        return queryWithPagination(queryExpression, page, pageSize, Delivery.class)
                .stream()
                .sorted(Comparator.comparing(Delivery::getEstimatedDeliveryAt))
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

        return new QueryAndFilterExpressions(keyConditionExpression, filterExpression.toString(), eav);
    }

    public List<Delivery> findPendingDeliveriesByProvider(String storeId, String provider, String excludedDeliveryId) {
        Delivery deliveryKey = new Delivery();
        deliveryKey.setStoreId(storeId);

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":null", new AttributeValue().withNULL(true));
        expressionAttributeValues.put(":provider", new AttributeValue().withS(provider));

        DynamoDBQueryExpression<Delivery> queryExpression = new DynamoDBQueryExpression<Delivery>()
                .withHashKeyValues(deliveryKey)
                .withFilterExpression("provider = :provider AND (attribute_not_exists(receivedAt) OR receivedAt = :null)")
                .withExpressionAttributeValues(expressionAttributeValues);

        return dynamoDBMapper.query(Delivery.class, queryExpression)
                .stream()
                .filter(d -> !d.getDeliveryId().equals(excludedDeliveryId))
                .sorted(Comparator.comparing(Delivery::getOrderedAt))
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
