package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The last cancel command sent for a shipment's courier order and where it stands. A shipment without one has none;
 * a confirmed cancellation clears the shipment, so there is no "succeeded" state. The failure reason is not kept: it
 * is logged where the failure is written.
 */
@DynamoDBDocument
public class ShipmentCancellation {

    /** A PENDING command older than this has lost its check message: nothing will answer it any more. */
    public static final Duration STALE = Duration.ofMinutes(5);

    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private ShipmentCancellationStatus status;
    @DynamoDBAttribute(attributeName = "commandId")
    private String commandId;
    @DynamoDBAttribute(attributeName = "requestedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime requestedAt;

    // required by dynamodb
    public ShipmentCancellation() {
    }

    private ShipmentCancellation(ShipmentCancellationStatus status, String commandId, LocalDateTime requestedAt) {
        this.status = status;
        this.commandId = commandId;
        this.requestedAt = requestedAt;
    }

    public static ShipmentCancellation pending(String commandId, LocalDateTime now) {
        return new ShipmentCancellation(ShipmentCancellationStatus.PENDING, commandId, now);
    }

    /** The same command, refused by the provider. A copy, so a remembered earlier state stays as it was. */
    public ShipmentCancellation failed() {
        return new ShipmentCancellation(ShipmentCancellationStatus.FAILED, commandId, requestedAt);
    }

    /** The same command, its result unknown after the checks ran out. A copy, like failed(). */
    public ShipmentCancellation unconfirmed() {
        return new ShipmentCancellation(ShipmentCancellationStatus.UNCONFIRMED, commandId, requestedAt);
    }

    public ShipmentCancellationStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentCancellationStatus status) {
        this.status = status;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    @DynamoDBIgnore
    public boolean isPending() {
        return status == ShipmentCancellationStatus.PENDING;
    }

    /** A PENDING command younger than STALE: a new request must wait for its result. */
    @DynamoDBIgnore
    public boolean isInProgress(LocalDateTime now) {
        return isPending() && !isStale(now);
    }

    /**
     * The result of the command is unknown: the checks ran out, or a PENDING one is so old its check message must
     * have been lost. Asking again reads that command instead of sending a new one.
     */
    @DynamoDBIgnore
    public boolean needsRecheck(LocalDateTime now) {
        return status == ShipmentCancellationStatus.UNCONFIRMED || (isPending() && isStale(now));
    }

    /** The command failed or its result is unknown: the label may or may not still be paid at the carrier. */
    @DynamoDBIgnore
    public boolean isUnresolved() {
        return status == ShipmentCancellationStatus.FAILED || status == ShipmentCancellationStatus.UNCONFIRMED;
    }

    @DynamoDBIgnore
    public boolean hasCommand(String id) {
        return commandId != null && commandId.equals(id);
    }

    private boolean isStale(LocalDateTime now) {
        return requestedAt == null || requestedAt.plus(STALE).isBefore(now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShipmentCancellation other)) {
            return false;
        }
        return status == other.status && Objects.equals(commandId, other.commandId)
                && Objects.equals(requestedAt, other.requestedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, commandId, requestedAt);
    }

    @Override
    public String toString() {
        return "ShipmentCancellation{status=" + status + ", commandId=" + commandId + ", requestedAt=" + requestedAt + "}";
    }
}
