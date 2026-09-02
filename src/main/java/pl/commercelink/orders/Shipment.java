package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

import static org.apache.commons.lang3.StringUtils.isEmpty;
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
    @DynamoDBAttribute(attributeName = "collectionPointCode")
    private String collectionPointCode;
    @DynamoDBAttribute(attributeName = "shippedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime shippedAt;
    @DynamoDBAttribute(attributeName = "deliveredAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime deliveredAt;
    @DynamoDBAttribute(attributeName = "trackingSubscriptionStatus")
    @DynamoDBTypeConvertedEnum
    private ShipmentTrackingStatus trackingSubscriptionStatus;
    @DynamoDBAttribute(attributeName = "trackingSubscriptionId")
    private String trackingSubscriptionId;
    @DynamoDBAttribute(attributeName = "trackingExternalId")
    private String trackingExternalId;
    @DynamoDBAttribute(attributeName = "trackingSubscriptionError")
    private String trackingSubscriptionError;
    @DynamoDBAttribute(attributeName = "trackingSubscribedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime trackingSubscribedAt;

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

    public String getCollectionPointCode() {
        return collectionPointCode;
    }

    public void setCollectionPointCode(String collectionPointCode) {
        this.collectionPointCode = collectionPointCode;
    }

    void inheritDeliveryChoiceFrom(Shipment previous) {
        if (collectionPointCode == null) {
            collectionPointCode = previous.collectionPointCode;
        }
        if (isEmpty(carrier)) {
            carrier = previous.carrier;
        }
        if (collectionPointCode != null && type == ShipmentType.Courier) {
            type = ShipmentType.PickupPoint;
        }
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
    public boolean isDeliveredToCollectionPoint() {
        return isNotEmpty(collectionPointCode);
    }

    @DynamoDBIgnore
    public boolean hasShippingData() {
        return isCarrierShipment() && isNotEmpty(carrier) && isNotEmpty(trackingNo) && shippedAt != null;
    }

    private boolean isCarrierShipment() {
        return type == ShipmentType.Courier || type == ShipmentType.PickupPoint;
    }

    public String getTrackingUrl() {
        return trackingUrl;
    }

    public void setTrackingUrl(String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    public ShipmentTrackingStatus getTrackingSubscriptionStatus() {
        return trackingSubscriptionStatus;
    }

    public void setTrackingSubscriptionStatus(ShipmentTrackingStatus trackingSubscriptionStatus) {
        this.trackingSubscriptionStatus = trackingSubscriptionStatus;
    }

    public String getTrackingSubscriptionId() {
        return trackingSubscriptionId;
    }

    public void setTrackingSubscriptionId(String trackingSubscriptionId) {
        this.trackingSubscriptionId = trackingSubscriptionId;
    }

    public String getTrackingExternalId() {
        return trackingExternalId;
    }

    public void setTrackingExternalId(String trackingExternalId) {
        this.trackingExternalId = trackingExternalId;
    }

    public String getTrackingSubscriptionError() {
        return trackingSubscriptionError;
    }

    public void setTrackingSubscriptionError(String trackingSubscriptionError) {
        this.trackingSubscriptionError = trackingSubscriptionError;
    }

    public LocalDateTime getTrackingSubscribedAt() {
        return trackingSubscribedAt;
    }

    public void setTrackingSubscribedAt(LocalDateTime trackingSubscribedAt) {
        this.trackingSubscribedAt = trackingSubscribedAt;
    }

    @DynamoDBIgnore
    public boolean hasTrackingSubscription() {
        return trackingSubscriptionStatus != null;
    }

    @DynamoDBIgnore
    public boolean isTrackingPending() {
        return trackingSubscriptionStatus == ShipmentTrackingStatus.PENDING;
    }

    public void markTrackingPending(String subscriptionId, LocalDateTime at) {
        this.trackingSubscriptionStatus = ShipmentTrackingStatus.PENDING;
        this.trackingSubscriptionId = subscriptionId;
        this.trackingSubscriptionError = null;
        this.trackingSubscribedAt = at;
    }

    public void markTrackingActive(String externalId, LocalDateTime at) {
        this.trackingSubscriptionStatus = ShipmentTrackingStatus.ACTIVE;
        this.trackingExternalId = externalId;
        this.trackingSubscriptionError = null;
        this.trackingSubscribedAt = at;
    }

    public void markTrackingFailed(String error, LocalDateTime at) {
        this.trackingSubscriptionStatus = ShipmentTrackingStatus.FAILED;
        this.trackingSubscriptionError = error;
        this.trackingSubscribedAt = at;
    }

    public void inheritTrackingSubscriptionFrom(Shipment previous) {
        if (previous == null || !previous.hasTrackingNo(trackingNo)) {
            return;
        }
        this.trackingSubscriptionStatus = previous.trackingSubscriptionStatus;
        this.trackingSubscriptionId = previous.trackingSubscriptionId;
        this.trackingExternalId = previous.trackingExternalId;
        this.trackingSubscriptionError = previous.trackingSubscriptionError;
        this.trackingSubscribedAt = previous.trackingSubscribedAt;
        if (externalId == null) {
            this.externalId = previous.externalId;
        }
    }
}
