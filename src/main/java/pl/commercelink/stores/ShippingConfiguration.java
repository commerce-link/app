package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@DynamoDBDocument
public class ShippingConfiguration {

    @DynamoDBAttribute(attributeName = "pickUpAddresses")
    private List<ShippingDetails> pickUpAddresses = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "senderAddresses")
    private List<ShippingDetails> senderAddresses = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "packageTemplates")
    private List<PackageTemplate> packageTemplates = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "authorizedCarriers")
    private List<AuthorizedCarrier> authorizedCarriers = new LinkedList<>();

    public ShippingConfiguration() {
    }

    public List<ShippingDetails> getPickUpAddresses() {
        return pickUpAddresses;
    }

    public void setPickUpAddresses(List<ShippingDetails> pickUpAddresses) {
        this.pickUpAddresses = pickUpAddresses;
    }

    public List<ShippingDetails> getSenderAddresses() {
        return senderAddresses;
    }

    public void setSenderAddresses(List<ShippingDetails> senderAddresses) {
        this.senderAddresses = senderAddresses;
    }

    public List<PackageTemplate> getPackageTemplates() {
        return packageTemplates;
    }

    public void setPackageTemplates(List<PackageTemplate> packageTemplates) {
        this.packageTemplates = packageTemplates;
    }

    public List<AuthorizedCarrier> getAuthorizedCarriers() {
        return authorizedCarriers;
    }

    public void setAuthorizedCarriers(List<AuthorizedCarrier> authorizedCarriers) {
        this.authorizedCarriers = authorizedCarriers;
    }

    @DynamoDBIgnore
    public ShippingDetails getPickUpAddress(String pickUpAddressId) {
        return pickUpAddresses.stream()
                .filter(address -> address.getId().equals(pickUpAddressId))
                .findFirst()
                .orElse(null);
    }

    @DynamoDBIgnore
    public Optional<ShippingDetails> getDefaultPickUpAddress() {
        return pickUpAddresses.stream()
                .filter(ShippingDetails::is_default)
                .findFirst();
    }

    @DynamoDBIgnore
    public Optional<ShippingDetails> getDefaultSenderAddress() {
        return senderAddresses.stream()
                .filter(ShippingDetails::is_default)
                .findFirst();
    }

    public PackageTemplate getPackageTemplate(String templateId) {
        return packageTemplates.stream()
                .filter(template -> template.getId().equals(templateId))
                .findFirst()
                .orElse(null);
    }
}
