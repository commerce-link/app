package pl.commercelink.orders.event;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.UUID;

@DynamoDBTable(tableName = "OrderEvents")
public class OrderEvent {

    /** The platform administrator rejected a delivery request that carried items of this order; details = "{supplier}[ · {reason}]". */
    public static final String DELIVERY_REQUEST_REJECTED = "DELIVERY_REQUEST_REJECTED";

    @DynamoDBHashKey(attributeName = "orderId")
    private String orderId;
    @DynamoDBRangeKey(attributeName = "eventId")
    private String eventId;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private EventType type;
    @DynamoDBAttribute(attributeName = "name")
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "NameIndex", attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
    /** Free text shown with the event (e.g. the supplier and the reason of a rejected delivery request); null for most events. */
    @DynamoDBAttribute(attributeName = "details")
    private String details;
    @DynamoDBVersionAttribute
    private Long version;

    public OrderEvent() {
    }

    public OrderEvent(String orderId, EventType type, String name, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
