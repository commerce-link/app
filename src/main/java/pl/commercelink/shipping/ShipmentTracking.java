package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

@DynamoDBTable(tableName = "ShipmentTrackings")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentTracking {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "trackingNo")
    private String trackingNo;
    @DynamoDBAttribute(attributeName = "orderId")
    private String orderId;
    @DynamoDBAttribute(attributeName = "rmaId")
    private String rmaId;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;

    public ShipmentTracking(String storeId, String trackingNo, String orderId, String rmaId, LocalDateTime createdAt) {
        this.storeId = storeId;
        this.trackingNo = trackingNo;
        this.orderId = orderId;
        this.rmaId = rmaId;
        this.createdAt = createdAt;
    }
}
