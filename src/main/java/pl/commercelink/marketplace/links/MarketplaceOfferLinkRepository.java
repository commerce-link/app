package pl.commercelink.marketplace.links;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MarketplaceOfferLinkRepository extends DynamoDbRepository<MarketplaceOfferLink> {

    private static final DynamoDBMapperConfig CONSISTENT = DynamoDBMapperConfig.builder()
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
            .build();

    public MarketplaceOfferLinkRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Optional<MarketplaceOfferLink> find(String storeId, String marketplace, String sellerOfferKey) {
        return Optional.ofNullable(dynamoDBMapper.load(MarketplaceOfferLink.class, storeId,
                MarketplaceOfferLink.linkKey(marketplace, sellerOfferKey), CONSISTENT));
    }

    public List<MarketplaceOfferLink> findByPimId(String storeId, String pimId) {
        DynamoDBQueryExpression<MarketplaceOfferLink> query = new DynamoDBQueryExpression<MarketplaceOfferLink>()
                .withIndexName(MarketplaceOfferLink.PIM_ID_INDEX)
                .withConsistentRead(false)
                .withKeyConditionExpression("storeId = :s and pimId = :p")
                .withExpressionAttributeValues(Map.of(
                        ":s", new AttributeValue(storeId),
                        ":p", new AttributeValue(pimId)));
        return dynamoDBMapper.query(MarketplaceOfferLink.class, query);
    }

    public List<MarketplaceOfferLink> findAll(String storeId, String marketplace) {
        DynamoDBQueryExpression<MarketplaceOfferLink> query = new DynamoDBQueryExpression<MarketplaceOfferLink>()
                .withKeyConditionExpression("storeId = :s and begins_with(linkKey, :p)")
                .withExpressionAttributeValues(Map.of(
                        ":s", new AttributeValue(storeId),
                        ":p", new AttributeValue(MarketplaceOfferLink.linkKey(marketplace, ""))))
                .withConsistentRead(true);
        return dynamoDBMapper.query(MarketplaceOfferLink.class, query);
    }
}
