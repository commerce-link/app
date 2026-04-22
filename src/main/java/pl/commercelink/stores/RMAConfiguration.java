package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class RMAConfiguration {

    @DynamoDBAttribute(attributeName = "carrier")
    private AuthorizedCarrier carrier;

    public RMAConfiguration() {
    }

    public AuthorizedCarrier getCarrier() {
        return carrier;
    }

    public void setCarrier(AuthorizedCarrier carrier) {
        this.carrier = carrier;
    }
}
