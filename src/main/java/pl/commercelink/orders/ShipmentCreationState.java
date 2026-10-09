package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The creation of a shipment while the provider has not created it yet: PENDING until the checker settles its
 * command, FAILED with the reason when it did not work. A created shipment has none.
 * <p>Treat it as immutable: transitions return new objects; the setters exist only for the DynamoDB mapper.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
public class ShipmentCreationState {

    /** The reason of a creation the provider never confirmed. */
    public static final String UNCONFIRMED_KEY = "shipping.creation.unconfirmed";

    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    private ShipmentCreationStatus status;
    @DynamoDBAttribute(attributeName = "command")
    private ProviderCommand command;

    private ShipmentCreationState(ShipmentCreationStatus status, ProviderCommand command) {
        this.status = status;
        this.command = command;
    }

    public static ShipmentCreationState pending(String commandId, LocalDateTime now) {
        return new ShipmentCreationState(ShipmentCreationStatus.PENDING, ProviderCommand.sent(commandId, now));
    }

    public ShipmentCreationState failed(String error) {
        return new ShipmentCreationState(ShipmentCreationStatus.FAILED, command.failed(error));
    }

    public ShipmentCreationState failedWithKey(String messageKey) {
        return new ShipmentCreationState(ShipmentCreationStatus.FAILED, command.failedWithKey(messageKey));
    }

    @DynamoDBIgnore
    public boolean isPending() {
        return status == ShipmentCreationStatus.PENDING;
    }

    @DynamoDBIgnore
    public boolean isFailed() {
        return status == ShipmentCreationStatus.FAILED;
    }

    /** PENDING and younger than ProviderCommandTimeout.UNCONFIRMED_AFTER: its result still comes. */
    @DynamoDBIgnore
    public boolean isInProgress(LocalDateTime now) {
        return isPending() && !isOverdue(now);
    }

    /** PENDING for so long that nothing will settle it any more: it reads and acts as a failed creation. */
    @DynamoDBIgnore
    public boolean isUnconfirmed(LocalDateTime now) {
        return isPending() && isOverdue(now);
    }

    @DynamoDBIgnore
    public boolean hasCommand(String id) {
        return command != null && command.hasId(id);
    }

    // a PENDING state without its command (never written so) counts as overdue, like one without a request time
    private boolean isOverdue(LocalDateTime now) {
        return command == null || command.isOverdue(now);
    }
}
