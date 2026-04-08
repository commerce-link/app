package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

import java.util.Objects;

@DynamoDBDocument
public class StoreNotification {

    @DynamoDBAttribute(attributeName = "severity")
    @DynamoDBTypeConvertedEnum
    private StoreNotificationSeverity severity;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private StoreNotificationType type;
    @DynamoDBAttribute(attributeName = "object")
    public String object;
    @DynamoDBAttribute(attributeName = "message")
    private String message;

    public StoreNotification() {
    }

    public StoreNotification(StoreNotificationSeverity severity, StoreNotificationType type, String object, String message) {
        this.severity = severity;
        this.type = type;
        this.object = object;
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StoreNotification that = (StoreNotification) o;
        return severity == that.severity && type == that.type && Objects.equals(object, that.object) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, type, object, message);
    }

    public StoreNotificationSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(StoreNotificationSeverity severity) {
        this.severity = severity;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StoreNotificationType getType() {
        return type;
    }

    public void setType(StoreNotificationType type) {
        this.type = type;
    }
}
