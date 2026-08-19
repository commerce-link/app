package pl.commercelink.inventory.deliveries;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

@DynamoDBDocument
public class Dropship {

    private String orderId;

    public Dropship() {
    }

    public Dropship(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Dropship requires an orderId");
        }
        this.orderId = orderId;
    }

    @DynamoDBAttribute(attributeName = "orderId")
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public boolean hasOrder() {
        return orderId != null && !orderId.isBlank();
    }
}
