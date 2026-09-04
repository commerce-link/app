package pl.commercelink.orders.rma;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import org.springframework.format.annotation.DateTimeFormat;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

/** One marketplace decision as published to SQS, kept so it can be republished verbatim (same commandId). */
@DynamoDBDocument
public class MarketplaceDecision {

    @DynamoDBAttribute(attributeName = "type")
    private String type;
    @DynamoDBAttribute(attributeName = "commandId")
    private String commandId;
    @DynamoDBAttribute(attributeName = "payload")
    private String payload;
    @DynamoDBAttribute(attributeName = "recordedAt")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime recordedAt;

    public MarketplaceDecision() {
    }

    public MarketplaceDecision(String type, String commandId, String payload, LocalDateTime recordedAt) {
        this.type = type;
        this.commandId = commandId;
        this.payload = payload;
        this.recordedAt = recordedAt;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
