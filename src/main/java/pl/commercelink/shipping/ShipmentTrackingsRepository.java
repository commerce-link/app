package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Shipment;
import pl.commercelink.shipping.tracking.ShipmentTrackingState;
import pl.commercelink.starter.dynamodb.DynamoDbRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    public List<ShipmentTracking> findByStore(String storeId) {
        ShipmentTracking key = new ShipmentTracking();
        key.setStoreId(storeId);
        List<ShipmentTracking> rows = new ArrayList<>();
        for (ShipmentTracking row : dynamoDBMapper.query(ShipmentTracking.class,
                new DynamoDBQueryExpression<ShipmentTracking>().withHashKeyValues(key))) {
            rows.add(row);
        }
        return rows;
    }

    /**
     * Moves the row to {@code to} only when it still has the state it was read with: of two writers applying the
     * same status (a webhook and a poll, two instances) exactly one gets true and carries out the effects.
     */
    public boolean advance(ShipmentTracking row, ShipmentTrackingState to) {
        String readState = row.getState();
        row.setState(to.name());
        boolean saved = saveIfStateIs(row, readState);
        if (!saved) {
            row.setState(readState);
        }
        return saved;
    }

    /** Remembers the poll time without ever writing back a state that another writer has moved meanwhile. */
    public boolean markPolled(ShipmentTracking row, LocalDateTime at) {
        row.setLastPolledAt(at);
        return saveIfStateIs(row, row.getState());
    }

    private boolean saveIfStateIs(ShipmentTracking row, String expectedState) {
        ExpectedAttributeValue expected = expectedState == null
                ? new ExpectedAttributeValue().withExists(false)
                : new ExpectedAttributeValue(new AttributeValue(expectedState));
        try {
            dynamoDBMapper.save(row, new DynamoDBSaveExpression().withExpected(Map.of("state", expected)));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
