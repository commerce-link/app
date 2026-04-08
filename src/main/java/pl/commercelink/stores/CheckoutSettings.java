package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;

import java.util.LinkedList;
import java.util.List;

@DynamoDBDocument
public class CheckoutSettings {

    @DynamoDBAttribute(attributeName = "successUrl")
    private String successUrl = "http://localhost:8080/success/";

    @DynamoDBAttribute(attributeName = "cancelUrl")
    private String cancelUrl = "http://localhost:8080/cancel";

    @DynamoDBAttribute(attributeName = "numberOfAcceptedPricelists")
    private int numberOfAcceptedPricelists = 1;

    @DynamoDBAttribute(attributeName = "currency")
    private String currency = "pln";

    @DynamoDBAttribute(attributeName = "deliveryOptions")
    private List<DeliveryOption> deliveryOptions = new LinkedList<>();

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public int getNumberOfAcceptedPricelists() {
        return numberOfAcceptedPricelists;
    }

    public void setNumberOfAcceptedPricelists(int numberOfAcceptedPricelists) {
        this.numberOfAcceptedPricelists = numberOfAcceptedPricelists;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public List<DeliveryOption> getDeliveryOptions() {
        return deliveryOptions;
    }

    public void setDeliveryOptions(List<DeliveryOption> deliveryOptions) {
        this.deliveryOptions = deliveryOptions;
    }

    public DeliveryOption findDeliveryOption(String deliveryOptionId) {
        return deliveryOptions.stream()
                .filter(o -> o.getId().equals(deliveryOptionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Delivery option not found: " + deliveryOptionId));
    }
}
