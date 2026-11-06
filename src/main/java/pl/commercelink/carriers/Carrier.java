package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of our carriers. The name is the identifier: it is the value of every dictionary pair and of Shipment.carrier,
 * so it never changes once saved, and a carrier is never deleted.
 */
@DynamoDBTable(tableName = Carrier.TABLE_NAME)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Carrier {

    public static final String TABLE_NAME = "Carriers";

    @DynamoDBHashKey(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "description")
    private String description;
}
