package pl.commercelink.warehouse.builtin;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;
import pl.commercelink.orders.FulfilmentStatus;

import java.util.*;

@Component
class WarehouseRepository extends DynamoDbRepository<WarehouseItem> {

    WarehouseRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    WarehouseItem findById(String storeId, String itemId) {
        return dynamoDBMapper.load(WarehouseItem.class, storeId, itemId);
    }

    List<WarehouseItem> findByDeliveryId(String storeId, String deliveryId) {
        return findByDeliveryIdAndStatuses(storeId, deliveryId, new LinkedList<>());
    }

    List<WarehouseItem> findByDeliveryIdAndStatuses(String storeId, String deliveryId, List<FulfilmentStatus> statuses) {
        Map<String, String> ean = new HashMap<>();
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        StringBuilder filterExpression = new StringBuilder("storeId = :storeId AND deliveryId = :deliveryId");

        if (!statuses.isEmpty()) {
            ean.put("#status", "status");
            StringBuilder statusFilter = new StringBuilder();
            for (int i = 0; i < statuses.size(); i++) {
                String key = ":status" + i;
                eav.put(key, new AttributeValue().withS(statuses.get(i).name()));
                if (i > 0) {
                    statusFilter.append(" OR ");
                }
                statusFilter.append("#status = ").append(key);
            }
            filterExpression.append(" AND (").append(statusFilter).append(")");
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(eav);

        if (!ean.isEmpty()) {
            scanExpression.withExpressionAttributeNames(ean);
        }

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression);
    }

    List<WarehouseItem> findAll(String storeId, FulfilmentStatus status) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":status", new AttributeValue().withS(status.name()));

        Map<String, String> ean = new HashMap<>();
        ean.put("#status", "status");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId AND #status = :status")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression);
    }

    List<WarehouseItem> findAllFiltered(String storeId, List<String> categories, List<FulfilmentStatus> statuses) {
        Map<String, AttributeValue> eav = new HashMap<>();
        Map<String, String> ean = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        StringBuilder filterExpression = new StringBuilder("storeId = :storeId");

        if (categories != null && !categories.isEmpty()) {
            ean.put("#category", "category");
            StringBuilder categoryFilter = new StringBuilder();
            for (int i = 0; i < categories.size(); i++) {
                String key = ":category" + i;
                eav.put(key, new AttributeValue().withS(categories.get(i)));
                if (i > 0) {
                    categoryFilter.append(" OR ");
                }
                categoryFilter.append("#category = ").append(key);
            }
            filterExpression.append(" AND (").append(categoryFilter).append(")");
        }

        if (statuses != null && !statuses.isEmpty()) {
            ean.put("#status", "status");
            StringBuilder statusFilter = new StringBuilder();
            for (int i = 0; i < statuses.size(); i++) {
                String key = ":status" + i;
                eav.put(key, new AttributeValue().withS(statuses.get(i).name()));
                if (i > 0) {
                    statusFilter.append(" OR ");
                }
                statusFilter.append("#status = ").append(key);
            }
            filterExpression.append(" AND (").append(statusFilter).append(")");
        }

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression.toString())
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression);
    }

    Set<String> findAllCategories(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId")
                .withExpressionAttributeValues(eav)
                .withProjectionExpression("category");

        List<WarehouseItem> items = dynamoDBMapper.scan(WarehouseItem.class, scanExpression);

        Set<String> categories = new HashSet<>();
        for (WarehouseItem item : items) {
            if (item.getCategory() != null) {
                categories.add(item.getCategory().name());
            }
        }
        return categories;
    }

    WarehouseItem findBySerialNo(String storeId, String serialNo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":serialNo", new AttributeValue().withS(serialNo));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId AND contains(serialNo, :serialNo)")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression)
                .stream()
                .findFirst()
                .orElse(null);
    }

    List<WarehouseItem> findAllByMfnAndStatus(String storeId, String mfn, FulfilmentStatus status) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":mfn", new AttributeValue().withS(mfn));
        eav.put(":status", new AttributeValue().withS(status.name()));

        Map<String, String> ean = new HashMap<>();
        ean.put("#status", "status");

        String filterExpression = "storeId = :storeId AND mfn = :mfn AND #status = :status";

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression)
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression);
    }

    List<WarehouseItem> findAllAvailableByMfns(String storeId, Collection<String> mfns) {
        Map<String, AttributeValue> eav = new HashMap<>();
        Map<String, String> ean = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":statusOrdered", new AttributeValue().withS(FulfilmentStatus.Ordered.name()));
        eav.put(":statusDelivered", new AttributeValue().withS(FulfilmentStatus.Delivered.name()));
        ean.put("#status", "status");

        StringBuilder inClause = new StringBuilder();
        int i = 0;
        for (String mfn : mfns) {
            String key = ":mfn" + i;
            eav.put(key, new AttributeValue().withS(mfn));
            if (i > 0) {
                inClause.append(", ");
            }
            inClause.append(key);
            i++;
        }

        String filterExpression = "storeId = :storeId AND mfn IN (" + inClause + ") AND (#status = :statusOrdered OR #status = :statusDelivered)";

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression)
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean);

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression);
    }

    List<WarehouseItem> findAllByMfns(String storeId, Collection<String> mfns) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        StringBuilder inClause = new StringBuilder();
        int i = 0;
        for (String mfn : mfns) {
            String key = ":mfn" + i;
            eav.put(key, new AttributeValue().withS(mfn));
            if (i > 0) {
                inClause.append(", ");
            }
            inClause.append(key);
            i++;
        }

        String filterExpression = "storeId = :storeId AND mfn IN (" + inClause + ")";

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression(filterExpression)
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(WarehouseItem.class, scanExpression);
    }

}
