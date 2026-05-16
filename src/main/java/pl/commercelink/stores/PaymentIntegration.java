package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class PaymentIntegration {

    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "default")
    private boolean _default;

    public PaymentIntegration() {
    }

    public PaymentIntegration(String name) {
        this.name = name;
    }

    public PaymentIntegration(String name, boolean isDefault) {
        this.name = name;
        this._default = isDefault;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean is_default() {
        return _default;
    }

    public void set_default(boolean isDefault) {
        this._default = isDefault;
    }
}
