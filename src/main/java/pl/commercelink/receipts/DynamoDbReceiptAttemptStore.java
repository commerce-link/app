package pl.commercelink.receipts;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
class DynamoDbReceiptAttemptStore extends DynamoDbRepository<ReceiptAttempt> implements ReceiptAttemptStore {

    private static final DynamoDBMapperConfig CONSISTENT = DynamoDBMapperConfig.builder()
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
            .build();

    DynamoDbReceiptAttemptStore(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    @Override
    public Optional<ReceiptAttempt> find(String storeId, String receiptKey) {
        return Optional.ofNullable(dynamoDBMapper.load(ReceiptAttempt.class, storeId, receiptKey, CONSISTENT));
    }

    @Override
    public List<ReceiptAttempt> findByOrder(String storeId, String orderId) {
        DynamoDBQueryExpression<ReceiptAttempt> query = new DynamoDBQueryExpression<ReceiptAttempt>()
                .withKeyConditionExpression("storeId = :s and begins_with(receiptKey, :p)")
                .withExpressionAttributeValues(Map.of(
                        ":s", new AttributeValue(storeId),
                        ":p", new AttributeValue(ReceiptAttemptKeys.orderPrefix(orderId))))
                .withConsistentRead(true);
        return dynamoDBMapper.query(ReceiptAttempt.class, query).stream()
                .sorted(Comparator.comparingInt(ReceiptAttempt::getAttemptNo))
                .toList();
    }

    @Override
    public Optional<ReceiptAttempt> findByProviderReceiptId(String storeId, String providerReceiptId) {
        DynamoDBQueryExpression<ReceiptAttempt> query = new DynamoDBQueryExpression<ReceiptAttempt>()
                .withKeyConditionExpression("storeId = :s")
                .withFilterExpression("providerReceiptId = :p")
                .withExpressionAttributeValues(Map.of(
                        ":s", new AttributeValue(storeId),
                        ":p", new AttributeValue(providerReceiptId)));
        return dynamoDBMapper.query(ReceiptAttempt.class, query).stream().findFirst();
    }

    /** A new attempt has no version, so the mapper saves it only if no item with the key exists. */
    @Override
    public boolean create(ReceiptAttempt attempt) {
        attempt.setVersion(null);
        try {
            dynamoDBMapper.save(attempt);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public void save(ReceiptAttempt attempt) {
        dynamoDBMapper.save(attempt);
    }

    @Override
    public List<ReceiptAttempt> findDue(Instant now, int limit) {
        DynamoDBQueryExpression<ReceiptAttempt> query = new DynamoDBQueryExpression<ReceiptAttempt>()
                .withIndexName(ReceiptAttempt.DUE_INDEX)
                .withConsistentRead(false)
                .withKeyConditionExpression("dueBucket = :b and nextCheckAt <= :now")
                .withExpressionAttributeValues(Map.of(
                        ":b", new AttributeValue(ReceiptAttempt.DUE_BUCKET),
                        ":now", new AttributeValue(ReceiptInstantConverter.format(now))))
                .withLimit(limit);
        return dynamoDBMapper.queryPage(ReceiptAttempt.class, query).getResults();
    }

    @Override
    public boolean hasLiveAttempts(String storeId) {
        DynamoDBQueryExpression<ReceiptAttempt> query = new DynamoDBQueryExpression<ReceiptAttempt>()
                .withKeyConditionExpression("storeId = :s")
                .withFilterExpression("#st IN (:issuing, :pending) "
                        + "OR (#st = :fiscalised AND attribute_not_exists(documentUrl) AND attribute_not_exists(linkGaveUpAt))")
                .withExpressionAttributeNames(Map.of("#st", "state"))
                .withExpressionAttributeValues(Map.of(
                        ":s", new AttributeValue(storeId),
                        ":issuing", new AttributeValue(ReceiptAttemptState.ISSUING.name()),
                        ":pending", new AttributeValue(ReceiptAttemptState.PENDING.name()),
                        ":fiscalised", new AttributeValue(ReceiptAttemptState.FISCALISED.name())));
        return !dynamoDBMapper.query(ReceiptAttempt.class, query).isEmpty();
    }
}
