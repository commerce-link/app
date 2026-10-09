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

    /**
     * Closes the row of a parcel that no longer exists (its shipment was cancelled) without any effect on the order:
     * EXPIRED is final, so the sweep stops polling it. A missing row, one already final or one moved by another writer
     * is left alone.
     */
    public boolean expireSilently(String storeId, String trackingNo) {
        Optional<ShipmentTracking> found = find(storeId, trackingNo);
        if (found.isEmpty() || found.get().hasUnknownState()
                || !ShipmentTrackingState.isForward(found.get().currentState(), ShipmentTrackingState.EXPIRED)) {
            return false;
        }
        return advance(found.get(), ShipmentTrackingState.EXPIRED);
    }

    /** Puts back the state the row was read with, only while it still holds the one {@link #advance} wrote. */
    public boolean revert(ShipmentTracking row, String previousState) {
        String advancedState = row.getState();
        row.setState(previousState);
        boolean saved = saveIfStateIs(row, advancedState);
        if (!saved) {
            row.setState(advancedState);
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
