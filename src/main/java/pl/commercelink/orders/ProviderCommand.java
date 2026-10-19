package pl.commercelink.orders;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

/**
 * A command sent to a shipping integration (the creation of a shipment, the order of a pickup): its id, when it was
 * sent and, once it did not work, why. Where the command stands (pending, failed, ordered) is the holder's status.
 * A failure found before anything was sent (no pickup windows, the request broke off) has a reason and no id.
 * <p>Treat it as immutable: transitions return new objects; the setters exist only for the DynamoDB mapper.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
public class ProviderCommand {

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

    private ProviderCommand(String commandId, LocalDateTime requestedAt, String error, String errorKey) {
        this.commandId = commandId;
        this.requestedAt = requestedAt;
        this.error = error;
        this.errorKey = errorKey;
    }

    public static ProviderCommand sent(String commandId, LocalDateTime now) {
        return new ProviderCommand(commandId, now, null, null);
    }

    static ProviderCommand notSent() {
        return new ProviderCommand(null, null, null, null);
    }

    /** The same command, refused in the provider's words. */
    public ProviderCommand failed(String error) {
        return new ProviderCommand(commandId, requestedAt, error, null);
    }

    /** The same command, ended for a reason of ours (a message key). */
    public ProviderCommand failedWithKey(String messageKey) {
        return new ProviderCommand(commandId, requestedAt, null, messageKey);
    }

    /** Sent so long ago (ProviderCommandTimeout) that, if still unanswered, nothing will answer it any more. */
    @DynamoDBIgnore
    public boolean isOverdue(LocalDateTime now) {
        return ProviderCommandTimeout.isOverdue(requestedAt, now);
    }

    @DynamoDBIgnore
    public boolean hasId(String id) {
        return commandId != null && commandId.equals(id);
    }

    /** The reason for a log line: our message key, or the provider's words. */
    @DynamoDBIgnore
    public String failureReason() {
        return errorKey != null ? errorKey : error;
    }
}
