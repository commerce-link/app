package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The courier pickup of a shipment's package. The window is kept as text (date yyyy-MM-dd, hours HH:mm) the way it is
 * shown. <p>Treat it as immutable: transitions return new objects; the setters exist only for the DynamoDB mapper.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
public class ShipmentPickup {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private ShipmentPickupStatus status;
    @DynamoDBAttribute(attributeName = "commandId")
    private String commandId;
    @DynamoDBAttribute(attributeName = "pickupId")
    private String pickupId;
    @DynamoDBAttribute(attributeName = "date")
    private String date;
    @DynamoDBAttribute(attributeName = "from")
    private String from;
    @DynamoDBAttribute(attributeName = "to")
    private String to;
    @DynamoDBAttribute(attributeName = "requestedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime requestedAt;
    @DynamoDBAttribute(attributeName = "error")
    private String error;
    @DynamoDBAttribute(attributeName = "errorKey")
    private String errorKey;

    private ShipmentPickup(ShipmentPickupStatus status, String commandId, String pickupId, String date, String from,
                           String to, LocalDateTime requestedAt, String error, String errorKey) {
        this.status = status;
        this.commandId = commandId;
        this.pickupId = pickupId;
        this.date = date;
        this.from = from;
        this.to = to;
        this.requestedAt = requestedAt;
        this.error = error;
        this.errorKey = errorKey;
    }

    public static ShipmentPickup notRequired() {
        return new ShipmentPickup(ShipmentPickupStatus.NOT_REQUIRED, null, null, null, null, null, null, null, null);
    }

    public static ShipmentPickup awaiting() {
        return new ShipmentPickup(ShipmentPickupStatus.AWAITING, null, null, null, null, null, null, null, null);
    }

    public static ShipmentPickup pending(String commandId, LocalDateTime now, LocalDate date, LocalTime from, LocalTime to) {
        return new ShipmentPickup(ShipmentPickupStatus.PENDING, commandId, null, date.toString(), from.format(HOUR),
                to.format(HOUR), now, null, null);
    }

    public ShipmentPickup ordered(String id) {
        return new ShipmentPickup(ShipmentPickupStatus.ORDERED, commandId, id, date, from, to, requestedAt, null, null);
    }

    public ShipmentPickup failed(String reason) {
        return new ShipmentPickup(ShipmentPickupStatus.FAILED, commandId, null, date, from, to, requestedAt, reason, null);
    }

    public ShipmentPickup failedWithKey(String messageKey) {
        return new ShipmentPickup(ShipmentPickupStatus.FAILED, commandId, null, date, from, to, requestedAt, null, messageKey);
    }

    /** The pickup can be ordered: waiting for the first order, or the last one did not work. */
    @DynamoDBIgnore
    public boolean isAwaiting() {
        return status == ShipmentPickupStatus.AWAITING || status == ShipmentPickupStatus.FAILED;
    }

    @DynamoDBIgnore
    public boolean isPending() {
        return status == ShipmentPickupStatus.PENDING;
    }

    @DynamoDBIgnore
    public boolean isPendingFor(String id) {
        return isPending() && commandId != null && commandId.equals(id);
    }

    @DynamoDBIgnore
    public boolean isOrdered() {
        return status == ShipmentPickupStatus.ORDERED;
    }

    @DynamoDBIgnore
    public boolean isFailed() {
        return status == ShipmentPickupStatus.FAILED;
    }
}
