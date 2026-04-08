package pl.commercelink.orders.event;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

@DynamoDBDocument
public class Event {
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private EventType type;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;

    public Event() {
    }

    public Event(EventType type, String name, LocalDateTime createdAt) {
        this.type = type;
        this.name = name;
        this.createdAt = createdAt;
    }

    @DynamoDBIgnore
    public boolean isSameAs(Event other) {
        return type.equals(other.type) && name.equals(other.name);
    }

    // required by dynamodb
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
