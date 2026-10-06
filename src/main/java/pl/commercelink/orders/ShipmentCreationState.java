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

import java.time.LocalDateTime;

/**
 * The creation command of a shipment while the provider has not created it yet: PENDING until the checker settles it,
 * FAILED with the reason when it did not work. A created shipment has none.
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
    @DynamoDBAttribute(attributeName = "commandId")
    private String commandId;
    @DynamoDBAttribute(attributeName = "requestedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime requestedAt;
    /** The provider's own words, shown as they are. */
    @DynamoDBAttribute(attributeName = "error")
    private String error;
    /** Our reason, as a message key (e.g. the provider never confirmed the command). */
    @DynamoDBAttribute(attributeName = "errorKey")
    private String errorKey;

    private ShipmentCreationState(ShipmentCreationStatus status, String commandId, LocalDateTime requestedAt,
                                  String error, String errorKey) {
        this.status = status;
        this.commandId = commandId;
        this.requestedAt = requestedAt;
        this.error = error;
        this.errorKey = errorKey;
    }

    public static ShipmentCreationState pending(String commandId, LocalDateTime now) {
        return new ShipmentCreationState(ShipmentCreationStatus.PENDING, commandId, now, null, null);
    }

    public ShipmentCreationState failed(String error) {
        return new ShipmentCreationState(ShipmentCreationStatus.FAILED, commandId, requestedAt, error, null);
    }

    public ShipmentCreationState failedWithKey(String messageKey) {
        return new ShipmentCreationState(ShipmentCreationStatus.FAILED, commandId, requestedAt, null, messageKey);
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
        return isPending() && !ProviderCommandTimeout.isOverdue(requestedAt, now);
    }

    /** PENDING for so long that nothing will settle it any more: it reads and acts as a failed creation. */
    @DynamoDBIgnore
    public boolean isUnconfirmed(LocalDateTime now) {
        return isPending() && ProviderCommandTimeout.isOverdue(requestedAt, now);
    }

    @DynamoDBIgnore
    public boolean hasCommand(String id) {
        return commandId != null && commandId.equals(id);
    }
}
