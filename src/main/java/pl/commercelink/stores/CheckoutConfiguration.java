package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@DynamoDBDocument
public class CheckoutConfiguration {

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

    /** The options customers can choose from; retired ones are kept only for offers and baskets that already chose them. */
    @DynamoDBIgnore
    public List<DeliveryOption> getActiveDeliveryOptions() {
        return deliveryOptions.stream().filter(o -> !o.isRetired()).toList();
    }

    /** Options to offer when editing a basket: the active ones plus the one it already chose, even if since retired. */
    public List<DeliveryOption> deliveryOptionsFor(String chosenDeliveryOptionId) {
        return deliveryOptions.stream()
                .filter(o -> !o.isRetired() || o.getId().equals(chosenDeliveryOptionId))
                .toList();
    }

    @DynamoDBIgnore
    public Optional<DeliveryOption> findActiveDeliveryOption(String deliveryOptionId) {
        return deliveryOptions.stream()
                .filter(o -> !o.isRetired() && o.getId().equals(deliveryOptionId))
                .findFirst();
    }

    public void addDeliveryOption(DeliveryOption option) {
        // the list may be immutable when it was set in code (demo seeder) rather than loaded from DynamoDB
        deliveryOptions = new LinkedList<>(deliveryOptions);
        deliveryOptions.add(option);
    }

    /** @return false when no active option has this id */
    public boolean retireDeliveryOption(String deliveryOptionId) {
        Optional<DeliveryOption> option = findActiveDeliveryOption(deliveryOptionId);
        option.ifPresent(o -> o.setRetired(true));
        return option.isPresent();
    }

    public DeliveryOption findDeliveryOption(String deliveryOptionId) {
        return deliveryOptions.stream()
                .filter(o -> o.getId().equals(deliveryOptionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Delivery option not found: " + deliveryOptionId));
    }
}
