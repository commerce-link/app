package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@DynamoDBDocument
public class Shipment {

    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private ShipmentType type = ShipmentType.Courier;
    @DynamoDBAttribute(attributeName = "trackingNo")
    private String trackingNo;
    @DynamoDBAttribute(attributeName = "trackingUrl")
    private String trackingUrl;
    @DynamoDBAttribute(attributeName = "externalId")
    private String externalId;
    @DynamoDBAttribute(attributeName = "carrier")
    private String carrier;
    @DynamoDBAttribute(attributeName = "shippedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime shippedAt;
    @DynamoDBAttribute(attributeName = "deliveredAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime deliveredAt;

    public Shipment() {
    }

    public Shipment(ShipmentType type) {
        this.type = type;
    }

    // required by dynamodb
    public ShipmentType getType() {
        return type;
    }

    public void setType(ShipmentType type) {
        this.type = type;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    @DynamoDBIgnore
    public boolean hasTrackingNo(String other) {
        return trackingNo != null && trackingNo.equals(other);
    }

    @DynamoDBIgnore
    public boolean hasCollectionData() {
        return type == ShipmentType.PersonalCollection && shippedAt != null;
    }

    @DynamoDBIgnore
    public boolean hasShippingData() {
        return type == ShipmentType.Courier && isNotEmpty(carrier) && isNotEmpty(trackingNo) && shippedAt != null;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }
}
