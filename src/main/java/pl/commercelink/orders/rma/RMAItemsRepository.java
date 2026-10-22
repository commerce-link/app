package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RMAItemsRepository extends DynamoDbRepository<RMAItem> {

    public RMAItemsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public RMAItem findById(String rmaId, String rmaItemId) {
        return dynamoDBMapper.load(RMAItem.class, rmaId, rmaItemId);
    }

    /** Every item of the RMA, read by the table's partition key and ordered by rmaItemId. */
    public List<RMAItem> findByRmaId(String rmaId) {
        RMAItem key = new RMAItem();
        key.setRmaId(rmaId);
        DynamoDBQueryExpression<RMAItem> query = new DynamoDBQueryExpression<RMAItem>()
                .withHashKeyValues(key);
        return new ArrayList<>(dynamoDBMapper.query(RMAItem.class, query));
    }

    public List<RMAItem> findBySerialNo(String serialNo) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":serialNo", new AttributeValue().withS(serialNo));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("contains(serialNo, :serialNo)")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMAItem.class, scanExpression);
    }

    /** All RMA items referencing a given order item, regardless of the owning RMA's status. */
    public List<RMAItem> findByOrderItemId(String orderItemId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":itemId", new AttributeValue().withS(orderItemId));
        DynamoDBScanExpression scan = new DynamoDBScanExpression()
                .withFilterExpression("itemId = :itemId")
                .withExpressionAttributeValues(eav);
        return dynamoDBMapper.scan(RMAItem.class, scan);
    }

    /** RMA items referencing any of the order items: one scan per 100 ids instead of one scan per item. */
    public List<RMAItem> findByOrderItemIds(Collection<String> orderItemIds) {
        List<String> ids = List.copyOf(orderItemIds);
        List<RMAItem> found = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += 100) {
            List<String> chunk = ids.subList(from, Math.min(from + 100, ids.size()));
            Map<String, AttributeValue> eav = new HashMap<>();
            List<String> placeholders = new ArrayList<>();
            for (int i = 0; i < chunk.size(); i++) {
                eav.put(":i" + i, new AttributeValue().withS(chunk.get(i)));
                placeholders.add(":i" + i);
            }
            found.addAll(dynamoDBMapper.scan(RMAItem.class, new DynamoDBScanExpression()
                    .withFilterExpression("itemId IN (" + String.join(", ", placeholders) + ")")
                    .withExpressionAttributeValues(eav)));
        }
        return found;
    }

    public List<RMAItem> findByDeliveryId(String deliveryId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":deliveryId", new AttributeValue().withS(deliveryId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("deliveryId = :deliveryId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(RMAItem.class, scanExpression);
    }
}
