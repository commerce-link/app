package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The courier pickup of a shipment's package: where it stands, the carrier's pickup number once ordered, the window it
 * was ordered for and the last command we sent for it (with the failure reason when it did not work). Waiting for the
 * first order, handed in at a point or booked by the carrier with the shipment, it has neither window nor command.
 * <p>Treat it as immutable: transitions return new objects; the setters exist only for the DynamoDB mapper.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
public class ShipmentPickup {

    /** The reason of a pickup the provider never confirmed. */
    public static final String UNCONFIRMED_KEY = "shipping.pickup.unconfirmed";

    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private ShipmentPickupStatus status;
    @DynamoDBAttribute(attributeName = "pickupId")
    private String pickupId;
    @DynamoDBAttribute(attributeName = "window")
    private ShipmentPickupWindow window;
    @DynamoDBAttribute(attributeName = "command")
    private ProviderCommand command;

    private ShipmentPickup(ShipmentPickupStatus status, String pickupId, ShipmentPickupWindow window,
                           ProviderCommand command) {
        this.status = status;
        this.pickupId = pickupId;
        this.window = window;
        this.command = command;
    }

    public static ShipmentPickup notRequired() {
        return new ShipmentPickup(ShipmentPickupStatus.NOT_REQUIRED, null, null, null);
    }

    public static ShipmentPickup awaiting() {
        return new ShipmentPickup(ShipmentPickupStatus.AWAITING, null, null, null);
    }

    /**
     * A courier the carrier (or the provider) booked together with the shipment, e.g. for a return collected from a
     * customer: ordered from the start under the carrier's pickup number, with no window we know of.
     */
    public static ShipmentPickup bookedByCarrier(String pickupId) {
        return new ShipmentPickup(ShipmentPickupStatus.ORDERED, pickupId, null, null);
    }

    public static ShipmentPickup pending(String commandId, LocalDateTime now, LocalDate date, LocalTime from, LocalTime to) {
        return new ShipmentPickup(ShipmentPickupStatus.PENDING, null, ShipmentPickupWindow.of(date, from, to),
                ProviderCommand.sent(commandId, now));
    }

    public ShipmentPickup ordered(String id) {
        return new ShipmentPickup(ShipmentPickupStatus.ORDERED, id, window, command);
    }

    /** Refused in the provider's words; one that failed before any command was sent keeps the reason only. */
    public ShipmentPickup failed(String reason) {
        return new ShipmentPickup(ShipmentPickupStatus.FAILED, null, window, commandOrNotSent().failed(reason));
    }

    public ShipmentPickup failedWithKey(String messageKey) {
        return new ShipmentPickup(ShipmentPickupStatus.FAILED, null, window, commandOrNotSent().failedWithKey(messageKey));
    }

    private ProviderCommand commandOrNotSent() {
        return command != null ? command : ProviderCommand.notSent();
    }

    /**
     * The pickup can be ordered: waiting for the first order, the last one did not work, or it was never confirmed (a
     * late result of that command is dropped once a new one is sent, since it is pending for the new command only).
     */
    @DynamoDBIgnore
    public boolean isAwaiting() {
        return status == ShipmentPickupStatus.AWAITING || status == ShipmentPickupStatus.FAILED
                || isUnconfirmed(LocalDateTime.now());
    }

    /** PENDING and younger than ProviderCommandTimeout.UNCONFIRMED_AFTER: its result still comes. */
    @DynamoDBIgnore
    public boolean isInProgress(LocalDateTime now) {
        return isPending() && !isOverdue(now);
    }

    /** PENDING for so long that nothing will settle it any more. */
    @DynamoDBIgnore
    public boolean isUnconfirmed(LocalDateTime now) {
        return isPending() && isOverdue(now);
    }

    @DynamoDBIgnore
    public boolean isPending() {
        return status == ShipmentPickupStatus.PENDING;
    }

    @DynamoDBIgnore
    public boolean isPendingFor(String id) {
        return isPending() && command != null && command.hasId(id);
    }

    @DynamoDBIgnore
    public boolean isOrdered() {
        return status == ShipmentPickupStatus.ORDERED;
    }

    /** Ordered without a command of ours: the carrier booked it with the shipment, so no window is known. */
    @DynamoDBIgnore
    public boolean isBookedByCarrier() {
        return isOrdered() && command == null && window == null;
    }

    @DynamoDBIgnore
    public boolean isFailed() {
        return status == ShipmentPickupStatus.FAILED;
    }

    // a PENDING pickup without its command (never written so) counts as overdue, like one without a request time
    private boolean isOverdue(LocalDateTime now) {
        return command == null || command.isOverdue(now);
    }
}
