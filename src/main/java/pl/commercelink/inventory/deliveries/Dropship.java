package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

@DynamoDBDocument
public class Dropship {

    private String orderId;

    public Dropship() {
    }

    public Dropship(String orderId) {
        this.orderId = orderId;
    }

    @DynamoDBAttribute(attributeName = "orderId")
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @DynamoDBIgnore
    public boolean hasOrder() {
        return orderId != null && !orderId.isBlank();
    }
}
