package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class RMASettings {

    @DynamoDBAttribute(attributeName = "carrier")
    private AuthorizedCarrier carrier;

    public RMASettings() {
    }

    public AuthorizedCarrier getCarrier() {
        return carrier;
    }

    public void setCarrier(AuthorizedCarrier carrier) {
        this.carrier = carrier;
    }
}
