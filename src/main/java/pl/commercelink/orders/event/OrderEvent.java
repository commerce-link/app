package pl.commercelink.orders.event;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;
import java.util.UUID;

@DynamoDBTable(tableName = "OrderEvents")
public class OrderEvent {

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
}
