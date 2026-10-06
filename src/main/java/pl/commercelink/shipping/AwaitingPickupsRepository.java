package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import org.springframework.stereotype.Component;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.List;

@Component
public class AwaitingPickupsRepository extends DynamoDbRepository<AwaitingPickup> {

    public AwaitingPickupsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public List<AwaitingPickup> findByStore(String storeId) {
        AwaitingPickup key = new AwaitingPickup();
        key.setStoreId(storeId);
        return dynamoDBMapper.query(AwaitingPickup.class,
                new DynamoDBQueryExpression<AwaitingPickup>().withHashKeyValues(key).withConsistentRead(true));
    }

    public void delete(String storeId, String externalId) {
        AwaitingPickup key = new AwaitingPickup();
        key.setStoreId(storeId);
        key.setExternalId(externalId);
        dynamoDBMapper.delete(key);
    }
}
