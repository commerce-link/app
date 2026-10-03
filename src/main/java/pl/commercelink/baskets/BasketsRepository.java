package pl.commercelink.baskets;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * One page of the offers list (spec §5.2). The query reads only the light projection of the index (basketId, name,
     * type, expiresAt), so filtering, counting and slicing are cheap; only the shown page is then loaded in full.
     * A filter may use only projected attributes — hence no search by e-mail (spec §10).
     */
    public OfferListResult findForList(String storeId, OfferListCriteria criteria, int page, int pageSize) {
        Map<String, AttributeValue> values = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        values.put(":storeId", new AttributeValue().withS(storeId));
        values.put(":type", new AttributeValue().withS(criteria.type().name()));
        names.put("#type", "type");

        String keyCondition = "storeId = :storeId";
        String from = criteria.from() == null ? null : criteria.from().atStartOfDay().format(DATE_TIME_FORMATTER);
        // the whole "to" day: createdAt is a date-time string, so "<= 2026-09-30" alone would miss that day
        String to = criteria.to() == null ? null : criteria.to().atTime(LocalTime.MAX).format(DATE_TIME_FORMATTER);
        if (from != null && to != null) {
            keyCondition += " AND createdAt BETWEEN :from AND :to";
        } else if (from != null) {
            keyCondition += " AND createdAt >= :from";
        } else if (to != null) {
            keyCondition += " AND createdAt <= :to";
        }
        if (from != null) values.put(":from", new AttributeValue().withS(from));
        if (to != null) values.put(":to", new AttributeValue().withS(to));

        StringBuilder filter = new StringBuilder("#type = :type");
        if (criteria.text() != null) {
            values.put(":q", new AttributeValue().withS(criteria.text()));
            names.put("#name", "name");
            filter.append(" AND (contains(#name, :q) OR begins_with(basketId, :q))");
        }
        if (!criteria.validity().isEmpty()) {
            List<String> alternatives = new ArrayList<>();
            for (OfferValidity v : EnumSet.copyOf(criteria.validity())) {
                alternatives.add(switch (v) {
                    case ACTIVE -> "expiresAt > :soon";
                    case EXPIRING -> "(expiresAt >= :now AND expiresAt <= :soon)";
                    case EXPIRED -> "expiresAt < :now";
                    case NO_EXPIRY -> "attribute_not_exists(expiresAt)";
                });
            }
            String joined = String.join(" OR ", alternatives);
            if (joined.contains(":now")) values.put(":now", new AttributeValue().withS(criteria.now().format(DATE_TIME_FORMATTER)));
            if (joined.contains(":soon")) values.put(":soon", new AttributeValue().withS(
                    criteria.now().plusDays(OfferValidity.EXPIRING_DAYS).format(DATE_TIME_FORMATTER)));
            filter.append(" AND (").append(joined).append(")");
        }

        DynamoDBQueryExpression<Basket> query = new DynamoDBQueryExpression<Basket>()
                .withIndexName("BasketCreatedAtIndex")
                .withConsistentRead(false)
                .withKeyConditionExpression(keyCondition)
                .withFilterExpression(filter.toString())
                .withExpressionAttributeNames(names)
                .withExpressionAttributeValues(values)
                .withScanIndexForward(false);

        List<String> ids = dynamoDBMapper.query(Basket.class, query).stream().map(Basket::getBasketId).toList();
        int total = ids.size();
        int lastPage = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int shown = Math.min(Math.max(1, page), lastPage);
        List<String> pageIds = ids.subList(Math.min((shown - 1) * pageSize, total), Math.min(shown * pageSize, total));
        if (pageIds.isEmpty()) {
            return new OfferListResult(List.of(), total, shown);
        }

        List<Basket> keys = pageIds.stream().map(id -> {
            Basket key = new Basket();
            key.setStoreId(storeId);
            key.setBasketId(id);
            return key;
        }).toList();
        Map<String, Basket> loaded = dynamoDBMapper.batchLoad(keys).values().stream()
                .flatMap(List::stream)
                .map(Basket.class::cast)
                .collect(Collectors.toMap(Basket::getBasketId, b -> b, (a, b) -> a));
        // batchLoad has no order and skips a record deleted since the index read
        List<Basket> rows = pageIds.stream().map(loaded::get).filter(Objects::nonNull).toList();
        return new OfferListResult(rows, total, shown);
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
        oldBaskets.forEach(basket -> {
            try {
                delete(basket);
            } catch (ConditionalCheckFailedException e) {
                // Concurrent modification — next cleanup tick will re-evaluate this basket.
            }
        });
    }
}
