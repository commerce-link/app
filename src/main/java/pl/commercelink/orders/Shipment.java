package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class Shipment {

    public static final Duration STALE_CANCELLATION = Duration.ofMinutes(5);

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
    @DynamoDBAttribute(attributeName = "cancellationStatus")
    @DynamoDBTypeConvertedEnum
    private ShipmentCancellationStatus cancellationStatus;
    @DynamoDBAttribute(attributeName = "cancellationCommandId")
    private String cancellationCommandId;
    @DynamoDBAttribute(attributeName = "cancellationError")
    private String cancellationError;
    @DynamoDBAttribute(attributeName = "cancellationRequestedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime cancellationRequestedAt;

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

    public ShipmentCancellationStatus getCancellationStatus() {
        return cancellationStatus;
    }

    public void setCancellationStatus(ShipmentCancellationStatus cancellationStatus) {
        this.cancellationStatus = cancellationStatus;
    }

    public String getCancellationCommandId() {
        return cancellationCommandId;
    }

    public void setCancellationCommandId(String cancellationCommandId) {
        this.cancellationCommandId = cancellationCommandId;
    }

    public String getCancellationError() {
        return cancellationError;
    }

    public void setCancellationError(String cancellationError) {
        this.cancellationError = cancellationError;
    }

    public LocalDateTime getCancellationRequestedAt() {
        return cancellationRequestedAt;
    }

    public void setCancellationRequestedAt(LocalDateTime cancellationRequestedAt) {
        this.cancellationRequestedAt = cancellationRequestedAt;
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

    public void markCancellationPending(String commandId, LocalDateTime now) {
        this.cancellationStatus = ShipmentCancellationStatus.PENDING;
        this.cancellationCommandId = commandId;
        this.cancellationError = null;
        this.cancellationRequestedAt = now;
    }

    public void markCancellationFailed(String error) {
        this.cancellationStatus = ShipmentCancellationStatus.FAILED;
        this.cancellationError = error;
    }

    public void markCancellationUnconfirmed() {
        this.cancellationStatus = ShipmentCancellationStatus.UNCONFIRMED;
    }

    /**
     * Puts back the cancellation state the shipment had before a cancel command that was marked but never reached the
     * provider, so a refused request leaves no trace of itself.
     */
    public void restoreCancellation(ShipmentCancellationStatus status, String commandId, String error,
                                    LocalDateTime requestedAt) {
        this.cancellationStatus = status;
        this.cancellationCommandId = commandId;
        this.cancellationError = error;
        this.cancellationRequestedAt = requestedAt;
    }

    @DynamoDBIgnore
    public boolean isCancellationPending() {
        return cancellationStatus == ShipmentCancellationStatus.PENDING;
    }

    /** A PENDING cancellation younger than STALE_CANCELLATION: a new request must wait for its result. */
    @DynamoDBIgnore
    public boolean isCancellationInProgress(LocalDateTime now) {
        return isCancellationPending() && !isStale(now);
    }

    /**
     * The result of the last cancel command is unknown: the checks ran out, or a PENDING one is so old its check
     * message must have been lost. Asking again reads that command instead of sending a new one.
     */
    @DynamoDBIgnore
    public boolean needsCancellationRecheck(LocalDateTime now) {
        return cancellationStatus == ShipmentCancellationStatus.UNCONFIRMED || (isCancellationPending() && isStale(now));
    }

    /** The last cancellation failed or its result is unknown: the label may or may not still be paid at the carrier. */
    @DynamoDBIgnore
    public boolean isCancellationUnresolved() {
        return cancellationStatus == ShipmentCancellationStatus.FAILED
                || cancellationStatus == ShipmentCancellationStatus.UNCONFIRMED;
    }

    @DynamoDBIgnore
    public boolean hasCancellationCommand(String commandId) {
        return cancellationCommandId != null && cancellationCommandId.equals(commandId);
    }

    private boolean isStale(LocalDateTime now) {
        return cancellationRequestedAt == null || cancellationRequestedAt.plus(STALE_CANCELLATION).isBefore(now);
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
            this.cancellationStatus = previous.cancellationStatus;
            this.cancellationCommandId = previous.cancellationCommandId;
            this.cancellationError = previous.cancellationError;
            this.cancellationRequestedAt = previous.cancellationRequestedAt;
        }
    }

    /**
     * What is left of removed when the order's only shipment is removed: the customer's choice of delivery (the type,
     * the pickup point and, for a pickup point, its carrier), waiting to go out again. Its tracking, dates and courier
     * order go with the removal.
     */
    public static Shipment placeholderFor(Shipment removed) {
        Shipment placeholder = new Shipment(removed.type);
        placeholder.collectionPointCode = removed.collectionPointCode;
        if (removed.type == ShipmentType.PickupPoint) {
            placeholder.carrier = removed.carrier;
        }
        return placeholder;
    }

    /** Nothing but the delivery choice: no tracking, no dates, no courier order. */
    @DynamoDBIgnore
    public boolean isPlaceholder() {
        return isEmpty(trackingNo) && isEmpty(trackingUrl) && isEmpty(externalId) && shippedAt == null
                && deliveredAt == null;
    }
}
