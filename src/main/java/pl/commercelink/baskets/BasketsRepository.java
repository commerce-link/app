package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Repository
public class BasketsRepository extends DynamoDbRepository<Basket> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public BasketsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public List<Basket> findAll(String storeId) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId")
                .withExpressionAttributeValues(eav);

        return dynamoDBMapper.scan(Basket.class, scanExpression);
    }

    public List<Basket> findAllByType(String storeId, BasketType type) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":storeId", new AttributeValue().withS(storeId));
        eav.put(":type", new AttributeValue().withS(type.name()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#type", "type");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("storeId = :storeId and #type = :type")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(expressionAttributeNames);

        return dynamoDBMapper.scan(Basket.class, scanExpression);
    }

    public Optional<Basket> findById(String storeId, String basketId) {
        return Optional.ofNullable(dynamoDBMapper.load(Basket.class, storeId, basketId));
    }

    public List<Basket> search(String storeId, BasketFilter filter, int page, int pageSize) {
        if (isNotBlank(filter.getBasketId())) {
            Optional<Basket> basket = findById(storeId, filter.getBasketId());
            return basket.map(Collections::singletonList).orElseGet(Collections::emptyList);
        } else {
            Map<String, AttributeValue> eav = new HashMap<>();
            Map<String, String> expressionAttributeNames = new HashMap<>();
            StringBuilder filterExpression = new StringBuilder();
            eav.put(":storeId", new AttributeValue().withS(storeId));

            if (isNotBlank(filter.getNamePrefix())) {
                eav.put(":namePrefix", new AttributeValue().withS(filter.getNamePrefix()));
                expressionAttributeNames.put("#name", "name");
                appendFilter(filterExpression, "begins_with(#name, :namePrefix)");
            }
            if (filter.getType() != null) {
                eav.put(":type", new AttributeValue().withS(filter.getType().name()));
                expressionAttributeNames.put("#type", "type");
                appendFilter(filterExpression, "#type = :type");
            }

            String keyCondition = "storeId = :storeId";
            LocalDate createdAtStart = filter.getCreatedAtStart();
            LocalDate createdAtEnd = filter.getCreatedAtEnd();
            if (createdAtStart != null && createdAtEnd != null) {
                keyCondition += " AND createdAt BETWEEN :createdAtStart AND :createdAtEnd";
                eav.put(":createdAtStart", new AttributeValue().withS(createdAtStart.toString()));
                eav.put(":createdAtEnd", new AttributeValue().withS(createdAtEnd.toString()));
            } else if (createdAtStart != null) {
                keyCondition += " AND createdAt >= :createdAtStart";
                eav.put(":createdAtStart", new AttributeValue().withS(createdAtStart.toString()));
            } else if (createdAtEnd != null) {
                keyCondition += " AND createdAt <= :createdAtEnd";
                eav.put(":createdAtEnd", new AttributeValue().withS(createdAtEnd.toString()));
            }

            DynamoDBQueryExpression<Basket> queryExpression = new DynamoDBQueryExpression<Basket>()
                    .withIndexName("BasketCreatedAtIndex")
                    .withConsistentRead(false) // required for GSI
                    .withKeyConditionExpression(keyCondition)
                    .withExpressionAttributeValues(eav)
                    .withExpressionAttributeNames(expressionAttributeNames.isEmpty() ? null : expressionAttributeNames)
                    .withFilterExpression(filterExpression.length() > 0 ? filterExpression.toString() : null)
                    .withScanIndexForward(false);

            return queryWithPagination(queryExpression, page, pageSize, Basket.class);
        }
    }

    public void deleteAllBasketsOlderThan(LocalDateTime date) {
        String formattedDate = date.format(DATE_TIME_FORMATTER);

        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":thresholdDate", new AttributeValue().withS(formattedDate));
        eav.put(":type", new AttributeValue().withS(BasketType.Basket.name()));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#type", "type");

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("createdAt < :thresholdDate and #type = :type")
                .withExpressionAttributeValues(eav)
                .withExpressionAttributeNames(expressionAttributeNames);

        List<Basket> oldBaskets = dynamoDBMapper.scan(Basket.class, scanExpression);
        oldBaskets.forEach(this::delete);
    }
}
