package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CarrierMapping {

    @DynamoDBAttribute(attributeName = "externalText")
    private String externalText;
    @DynamoDBAttribute(attributeName = "carrier")
    private String carrier;
}
