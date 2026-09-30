package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
    /** The last cancel command of the courier order; null when none was sent. */
    @DynamoDBAttribute(attributeName = "cancellation")
    private ShipmentCancellation cancellation;

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

    /** Carriers (and Furgonetka's webhooks) echo tracking numbers upper-cased; compare and index them case-insensitively. */
    public static String normalizeTrackingNo(String trackingNo) {
        return trackingNo == null ? null : trackingNo.trim().toUpperCase(Locale.ROOT);
    }

    @DynamoDBIgnore
    public boolean hasTrackingNo(String other) {
        return trackingNo != null && other != null
                && normalizeTrackingNo(trackingNo).equals(normalizeTrackingNo(other));
    }

    @DynamoDBIgnore
    public boolean hasCollectionData() {
        return type == ShipmentType.PersonalCollection && shippedAt != null;
    }

    /** It has a shipped (or ready-for-collection) or a delivery date: what the card shows as "nadano" / "dostarczono". */
    @DynamoDBIgnore
    public boolean hasGoneOut() {
        return shippedAt != null || deliveredAt != null;
    }

    @DynamoDBIgnore
    public boolean isDeliveredToCollectionPoint() {
        return isNotBlank(collectionPointCode);
    }

    @DynamoDBIgnore
    public boolean hasLabel() {
        return isNotBlank(externalId) || isNotBlank(trackingNo);
    }

    @DynamoDBIgnore
    public boolean hasShippingData() {
        // a blank tracking number normalizes to an empty string, which is not a valid DynamoDB index key
        return isCarrierShipment() && isNotBlank(carrier) && isNotBlank(trackingNo) && shippedAt != null;
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

    public ShipmentCancellation getCancellation() {
        return cancellation;
    }

    public void setCancellation(ShipmentCancellation cancellation) {
        this.cancellation = cancellation;
    }

    @DynamoDBIgnore
    public boolean hasTrackingSubscription() {
        return trackingSubscriptionStatus != null;
    }

    @DynamoDBIgnore
    public boolean isTrackingPending() {
        return trackingSubscriptionStatus == ShipmentTrackingStatus.PENDING;
    }

    public void markTrackingPending(String subscriptionId) {
        this.trackingSubscriptionStatus = ShipmentTrackingStatus.PENDING;
        this.trackingSubscriptionId = subscriptionId;
    }

    public void markTrackingActive(String externalId) {
        this.trackingSubscriptionStatus = ShipmentTrackingStatus.ACTIVE;
        this.trackingExternalId = externalId;
    }

    public void markTrackingFailed() {
        this.trackingSubscriptionStatus = ShipmentTrackingStatus.FAILED;
    }

    /** A cancel command younger than ShipmentCancellation.STALE waits for its result: a new request must wait too. */
    @DynamoDBIgnore
    public boolean isCancellationInProgress(LocalDateTime now) {
        return cancellation != null && cancellation.isInProgress(now);
    }

    /** The result of the last cancel command is unknown: asking again reads that command, not a new one. */
    @DynamoDBIgnore
    public boolean needsCancellationRecheck(LocalDateTime now) {
        return cancellation != null && cancellation.needsRecheck(now);
    }

    /** The last cancellation failed or its result is unknown: the label may or may not still be paid at the carrier. */
    @DynamoDBIgnore
    public boolean isCancellationUnresolved() {
        return cancellation != null && cancellation.isUnresolved();
    }

    /** The shipment still waits for the result of that very cancel command. */
    @DynamoDBIgnore
    public boolean isCancellationPendingFor(String commandId) {
        return cancellation != null && cancellation.isPending() && cancellation.hasCommand(commandId);
    }

    /** The tracking subscription follows the tracking number: a changed number is tracked anew. */
    public void inheritTrackingSubscriptionFrom(Shipment previous) {
        if (previous == null || !previous.hasTrackingNo(trackingNo)) {
            return;
        }
        this.trackingSubscriptionStatus = previous.trackingSubscriptionStatus;
        this.trackingSubscriptionId = previous.trackingSubscriptionId;
        this.trackingExternalId = previous.trackingExternalId;
    }

    /**
     * The courier order (the paid label at the carrier) stays with the shipment whatever an edit does to its fields:
     * only "Cancel courier order" cancels it at the carrier, and a shipment that lost it could be removed and leave the
     * label orphaned. It keeps the state of its cancellation together with it.
     */
    public void inheritCourierOrderFrom(Shipment previous) {
        if (previous != null && previous.externalId != null) {
            this.externalId = previous.externalId;
            this.cancellation = previous.cancellation;
        }
    }

    /** Nothing but the delivery choice: no tracking, no dates, no courier order. */
    @DynamoDBIgnore
    public boolean isPlaceholder() {
        return isEmpty(trackingNo) && isEmpty(trackingUrl) && isEmpty(externalId) && shippedAt == null
                && deliveredAt == null;
    }
}
