package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrderItemsRepository extends DynamoDbRepository<OrderItem> {

    public OrderItemsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public OrderItem findById(String orderId, String orderItemId) {
        return dynamoDBMapper.load(OrderItem.class, orderId, orderItemId);
    }

    public List<OrderItem> findByOrderId(String orderId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":orderId", new AttributeValue().withS(orderId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("orderId = :orderId")
                .withExpressionAttributeValues(eav);

        return scanAndSort(scanExpression);
    }

    public List<OrderItem> findByDeliveryId(String deliveryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("deliveryId = :deliveryId")
                .withExpressionAttributeValues(eav);

        return scanAndSort(scanExpression);
    }

    public List<OrderItem> findByOrderIdAndStatus(String orderId, FulfilmentStatus status) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":orderId", new AttributeValue().withS(orderId));
        eav.put(":status", new AttributeValue().withS(status.name()));

        Map<String, String> ean = new HashMap<>();
        ean.put("#status", "status");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("orderId = :orderId and #status = :status")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return scanAndSort(scanExpression);
    }

    public List<OrderItem> findByOrderIdAndStatuses(String orderId, List<FulfilmentStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":orderId", new AttributeValue().withS(orderId));

        Map<String, String> ean = new HashMap<>();
        ean.put("#status", "status");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("orderId = :orderId and #status IN (" + buildStatusesFilter(statuses, eav) + ")")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return scanAndSort(scanExpression);
    }

    public List<String> findByDeliveryIdAndStatuses(String deliveryId, List<FulfilmentStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        Map<String, String> ean = new HashMap<>();
        ean.put("#status", "status");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("deliveryId = :deliveryId and #status IN (" + buildStatusesFilter(statuses, eav) + ")")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return dynamoDBMapper.scan(OrderItem.class, scanExpression)
                .stream()
                .map(OrderItem::getOrderId)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<OrderItem> findBySerialNo(String serialNo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":serialNo", new AttributeValue().withS(serialNo));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("contains(serialNo, :serialNo)")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(OrderItem.class, scanExpression);
    }

    public OrderItem findBySerialNoAndStatuses(String serialNo, List<FulfilmentStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":serialNo", new AttributeValue().withS(serialNo));

        Map<String, String> ean = new HashMap<>();
        ean.put("#status", "status");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("contains(serialNo, :serialNo) and #status IN (" + buildStatusesFilter(statuses, eav) + ")")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return dynamoDBMapper.scan(OrderItem.class, scanExpression)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String buildStatusesFilter(List<FulfilmentStatus> statuses, Map<String, AttributeValue> eav) {
        StringBuilder statusFilter = new StringBuilder();
        for (int i = 0; i < statuses.size(); i++) {
            String key = ":status" + i;
            eav.put(key, new AttributeValue().withS(statuses.get(i).name()));
            statusFilter.append(key);
            if (i < statuses.size() - 1) {
                statusFilter.append(", ");
            }
        }
        return statusFilter.toString();
    }

    public List<OrderItem> scanAndSort(DynamoDBScanExpression scanExpression) {
        return dynamoDBMapper.scan(OrderItem.class, scanExpression)
                .stream()
                .sorted(Comparator.comparing(OrderItem::getSequenceNumber))
                .collect(Collectors.toList());
    }

}
