package pl.commercelink.shipping;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;

import java.time.LocalDateTime;

/**
 * A package waiting for "Zamów odbiór", across the store's orders and RMAs. Only an index: the shipment on its owner
 * is the source of truth, and an entry whose shipment no longer waits is dropped when it is read.
 */
@DynamoDBTable(tableName = "AwaitingPickups")
@Getter
@Setter
@NoArgsConstructor
public class AwaitingPickup {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "externalId")
    private String externalId;
    @DynamoDBAttribute(attributeName = "provider")
    private String provider;
    @DynamoDBAttribute(attributeName = "carrier")
    private String carrier;
    @DynamoDBAttribute(attributeName = "pickUpAddressId")
    private String pickUpAddressId;
    @DynamoDBAttribute(attributeName = "ownerType")
    @DynamoDBTypeConvertedEnum
    private ShipmentOwnerType ownerType;
    @DynamoDBAttribute(attributeName = "ownerId")
    private String ownerId;
    @DynamoDBAttribute(attributeName = "trackingNo")
    private String trackingNo;
    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime createdAt;
}
