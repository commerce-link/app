package pl.commercelink.discounts;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class DiscountCodesRepository {

    private static final String STORE_ID = "storeId";
    private static final String CODE = "code";
    private static final String ACTIVE = "active";
    private static final String USED_COUNT = "usedCount";

    private final AmazonDynamoDB amazonDynamoDB;
    private final DynamoDBMapper mapper;

    public DiscountCodesRepository(AmazonDynamoDB amazonDynamoDB) {
        this.amazonDynamoDB = amazonDynamoDB;
        this.mapper = new DynamoDBMapper(amazonDynamoDB);
    }

    public boolean create(DiscountCode discountCode) {
        discountCode.setCode(DiscountCode.normalize(discountCode.getCode()));
        try {
            mapper.save(discountCode,
                    new DynamoDBSaveExpression().withExpectedEntry(CODE, new ExpectedAttributeValue(false)),
                    DynamoDBMapperConfig.SaveBehavior.PUT.config());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public Optional<DiscountCode> find(String storeId, String code) {
        String normalized = DiscountCode.normalize(code);
        if (!DiscountCode.isWellFormed(normalized)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.load(DiscountCode.class, storeId, normalized,
                DynamoDBMapperConfig.builder().withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT).build()));
    }

    public List<DiscountCode> findAll(String storeId) {
        DiscountCode hashKey = new DiscountCode();
        hashKey.setStoreId(storeId);
        return new ArrayList<>(mapper.query(DiscountCode.class,
                new DynamoDBQueryExpression<DiscountCode>().withHashKeyValues(hashKey)));
    }

    public void setActive(String storeId, String code, boolean active) {
        try {
            amazonDynamoDB.updateItem(new UpdateItemRequest()
                    .withTableName(DiscountCode.TABLE_NAME)
                    .withKey(key(storeId, code))
                    .withUpdateExpression("SET #active = :active")
                    .withConditionExpression("attribute_exists(#code)")
                    .addExpressionAttributeNamesEntry("#active", ACTIVE)
                    .addExpressionAttributeNamesEntry("#code", CODE)
                    .addExpressionAttributeValuesEntry(":active", new AttributeValue().withBOOL(active)));
        } catch (ConditionalCheckFailedException e) {
            log.warn("Discount code {} of store {} is gone, nothing to switch", code, storeId);
        }
    }

    public boolean delete(String storeId, String code) {
        try {
            amazonDynamoDB.deleteItem(new DeleteItemRequest()
                    .withTableName(DiscountCode.TABLE_NAME)
                    .withKey(key(storeId, code))
                    .withConditionExpression("#usedCount = :zero")
                    .addExpressionAttributeNamesEntry("#usedCount", USED_COUNT)
                    .addExpressionAttributeValuesEntry(":zero", new AttributeValue().withN("0")));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private static Map<String, AttributeValue> key(String storeId, String code) {
        return Map.of(STORE_ID, new AttributeValue(storeId), CODE, new AttributeValue(DiscountCode.normalize(code)));
    }
}
