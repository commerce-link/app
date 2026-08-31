package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class DeliveryFormCarrier {

    @DynamoDBAttribute(attributeName = "source")
    private String source;

    @DynamoDBAttribute(attributeName = "deliveryForm")
    private String deliveryForm;

    @DynamoDBAttribute(attributeName = "carrier")
    private String carrier;

    public DeliveryFormCarrier() {
    }

    public DeliveryFormCarrier(String source, String deliveryForm, String carrier) {
        this.source = source;
        this.deliveryForm = deliveryForm;
        this.carrier = carrier;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDeliveryForm() {
        return deliveryForm;
    }

    public void setDeliveryForm(String deliveryForm) {
        this.deliveryForm = deliveryForm;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public boolean describes(String source, String deliveryForm) {
        return this.source != null && this.source.equalsIgnoreCase(source)
                && this.deliveryForm != null && this.deliveryForm.equals(deliveryForm);
    }
}
