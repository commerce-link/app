package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.util.Map;
import java.util.Optional;

@Component
public class ShipmentTrackingsRepository extends DynamoDbRepository<ShipmentTracking> {

    public ShipmentTrackingsRepository(AmazonDynamoDB amazonDynamoDB) {
        super(amazonDynamoDB);
    }

    public Optional<ShipmentTracking> find(String storeId, String trackingNo) {
        return Optional.ofNullable(dynamoDBMapper.load(ShipmentTracking.class, storeId, Shipment.normalizeTrackingNo(trackingNo)));
    }

    public boolean saveIfAbsent(ShipmentTracking tracking) {
        tracking.setTrackingNo(Shipment.normalizeTrackingNo(tracking.getTrackingNo()));
        DynamoDBSaveExpression onlyIfAbsent = new DynamoDBSaveExpression()
                .withExpected(Map.of("trackingNo", new ExpectedAttributeValue().withExists(false)));
        try {
            dynamoDBMapper.save(tracking, onlyIfAbsent);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
