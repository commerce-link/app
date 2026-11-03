package pl.commercelink.carriers;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One pair of a dictionary: the text of the external system and the name of our carrier it stands for. */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CarrierMapping {

    @DynamoDBAttribute(attributeName = "text")
    private String text;
    @DynamoDBAttribute(attributeName = "carrier")
    private String carrier;
}
