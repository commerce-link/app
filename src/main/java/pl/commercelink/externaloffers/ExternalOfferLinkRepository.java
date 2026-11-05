package pl.commercelink.externaloffers;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ExternalOfferLinkRepository extends DynamoDbRepository<ExternalOfferLink> {

    private static final DynamoDBMapperConfig CONSISTENT = DynamoDBMapperConfig.builder()
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
            .build();

    public ExternalOfferLinkRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Optional<ExternalOfferLink> find(String storeId, String orderSource, String offerKey) {
        return Optional.ofNullable(dynamoDBMapper.load(ExternalOfferLink.class, storeId,
                ExternalOfferLink.linkKey(orderSource, offerKey), CONSISTENT));
    }

    /**
     * Writes the link unless an operator's (MANUAL) one exists, in one conditional write, so a manual link saved
     * concurrently is never overwritten by an automatic match.
     */
    public boolean saveUnlessManual(ExternalOfferLink link) {
        DynamoDBSaveExpression notManual = new DynamoDBSaveExpression()
                .withExpectedEntry("linkKey", new ExpectedAttributeValue(false))
                .withExpectedEntry("source", new ExpectedAttributeValue(new AttributeValue(ExternalOfferLinkSource.EAN.name())))
                .withConditionalOperator(ConditionalOperator.OR);
        try {
            dynamoDBMapper.save(link, notManual);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public List<ExternalOfferLink> findAll(String storeId, String orderSource) {
        DynamoDBQueryExpression<ExternalOfferLink> query = new DynamoDBQueryExpression<ExternalOfferLink>()
                .withKeyConditionExpression("storeId = :s and begins_with(linkKey, :p)")
                .withExpressionAttributeValues(Map.of(
                        ":s", new AttributeValue(storeId),
                        ":p", new AttributeValue(ExternalOfferLink.linkKey(orderSource, ""))))
                .withConsistentRead(true);
        return dynamoDBMapper.query(ExternalOfferLink.class, query);
    }
}
