package pl.commercelink.discounts;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
public class PromotionsRepository extends DynamoDbRepository<Promotion> {

    private static final String STORE_ID = "storeId";
    private static final String PROMOTION_ID = "promotionId";
    private static final String ACTIVE = "active";

    public PromotionsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Optional<Promotion> find(String storeId, String promotionId) {
        if (!isWellFormed(promotionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(dynamoDBMapper.load(Promotion.class, storeId, promotionId,
                DynamoDBMapperConfig.builder().withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT).build()));
    }

    public List<Promotion> findAll(String storeId) {
        Promotion hashKey = new Promotion();
        hashKey.setStoreId(storeId);
        return new ArrayList<>(dynamoDBMapper.query(Promotion.class,
                new DynamoDBQueryExpression<Promotion>().withHashKeyValues(hashKey)));
    }

    public void setActive(String storeId, String promotionId, boolean active) {
        try {
            amazonDynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(Promotion.TABLE_NAME)
                    .withKey(Map.of(STORE_ID, new AttributeValue(storeId), PROMOTION_ID, new AttributeValue(promotionId)))
                    .withUpdateExpression("SET #active = :active")
                    .withConditionExpression("attribute_exists(#promotionId)")
                    .addExpressionAttributeNamesEntry("#active", ACTIVE)
                    .addExpressionAttributeNamesEntry("#promotionId", PROMOTION_ID)
                    .addExpressionAttributeValuesEntry(":active", new AttributeValue().withBOOL(active)));
        } catch (ConditionalCheckFailedException e) {
            log.warn("Promotion {} of store {} is gone, nothing to switch", promotionId, storeId);
        }
    }

    private static boolean isWellFormed(String promotionId) {
        if (promotionId == null) {
            return false;
        }
        try {
            return UUID.fromString(promotionId).toString().equals(promotionId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
