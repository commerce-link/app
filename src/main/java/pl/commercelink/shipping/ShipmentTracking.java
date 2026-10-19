package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.shipping.tracking.ShipmentTrackingState;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

@DynamoDBTable(tableName = "ShipmentTrackings")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentTracking {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "trackingNo")
    private String trackingNo;
    @DynamoDBAttribute(attributeName = "orderId")
    private String orderId;
    @DynamoDBAttribute(attributeName = "rmaId")
    private String rmaId;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
    /** Integration that tracks the parcel (descriptor name); null on rows written before several integrations. */
    @DynamoDBAttribute(attributeName = "provider")
    private String provider;
    /** The integration's id of a shipment it created; null for a number typed in or reported by a supplier. */
    @DynamoDBAttribute(attributeName = "externalId")
    private String externalId;
    /** {@link ShipmentTrackingState} reached by the parcel; null = not collected yet. Written conditionally. */
    @DynamoDBAttribute(attributeName = "state")
    private String state;
    @DynamoDBAttribute(attributeName = "lastPolledAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime lastPolledAt;

    public ShipmentTracking(String storeId, String trackingNo, String orderId, String rmaId, LocalDateTime createdAt) {
        this.storeId = storeId;
        this.trackingNo = trackingNo;
        this.orderId = orderId;
        this.rmaId = rmaId;
        this.createdAt = createdAt;
    }

    public ShipmentTracking(String storeId, String trackingNo, String orderId, String rmaId, LocalDateTime createdAt,
                            String provider, String externalId) {
        this(storeId, trackingNo, orderId, rmaId, createdAt);
        this.provider = provider;
        this.externalId = externalId;
    }

    public ShipmentTrackingState currentState() {
        return ShipmentTrackingState.parse(state);
    }

    /** A state written by a newer version: this one neither polls nor advances the parcel, so it cannot undo it. */
    public boolean hasUnknownState() {
        return state != null && currentState() == null;
    }
}
