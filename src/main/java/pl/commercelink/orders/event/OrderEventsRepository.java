package pl.commercelink.orders.event;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrderEventsRepository extends DynamoDbRepository<OrderEvent> {

    public OrderEventsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public List<OrderEvent> findByOrderId(String orderId) {
        OrderEvent hashKey = new OrderEvent();

        hashKey.setOrderId(orderId);

        DynamoDBQueryExpression<OrderEvent> queryExpression = new DynamoDBQueryExpression<OrderEvent>()
                .withHashKeyValues(hashKey)
                .withConsistentRead(false);

        return dynamoDBMapper.query(OrderEvent.class, queryExpression)
                .stream()
                .sorted(Comparator.comparing(OrderEvent::getCreatedAt))
                .collect(Collectors.toList());
    }

    public boolean hasEvent(String orderId, EventType type, String name) {
        OrderEvent hashKey = new OrderEvent();
        hashKey.setOrderId(orderId);

        Condition nameCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(name));

        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":type", new AttributeValue().withS(type.name()));

        Map<String, String> ean = new HashMap<>();
        ean.put("#type", "type");

        DynamoDBQueryExpression<OrderEvent> queryExpression = new DynamoDBQueryExpression<OrderEvent>()
                .withIndexName("NameIndex")
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("name", nameCondition)
                .withFilterExpression("#type = :type")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(ean)
                .withConsistentRead(false);

        return !dynamoDBMapper.query(OrderEvent.class, queryExpression).isEmpty();
    }

    public void deleteByOrderIdAndName(String orderId, String name) {
        OrderEvent hashKey = new OrderEvent();
        hashKey.setOrderId(orderId);

        Condition nameCondition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ)
                .withAttributeValueList(new AttributeValue().withS(name));

        DynamoDBQueryExpression<OrderEvent> queryExpression = new DynamoDBQueryExpression<OrderEvent>()
                .withIndexName("NameIndex")
                .withHashKeyValues(hashKey)
                .withRangeKeyCondition("name", nameCondition)
                .withConsistentRead(false);

        List<OrderEvent> events = dynamoDBMapper.query(OrderEvent.class, queryExpression);
        for (OrderEvent event : events) {
            dynamoDBMapper.delete(event);
        }
    }
}
