package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.orders.ShippingDetails;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isBlank;

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

    @DynamoDBIgnore
    public Optional<ShippingDetails> findPickUpAddress(String id) {
        return pickUpAddresses.stream().filter(address -> id.equals(address.getId())).findFirst();
    }

    /** The first address of the store becomes its default, as does one added with {@code makeDefault}. */
    @DynamoDBIgnore
    public void addPickUpAddress(ShippingDetails address, boolean makeDefault) {
        if (isBlank(address.getId())) {
            address.setId(UUID.randomUUID().toString());
        }
        boolean becomesDefault = makeDefault || pickUpAddresses.stream().noneMatch(ShippingDetails::is_default);
        if (becomesDefault) {
            pickUpAddresses.forEach(other -> other.set_default(false));
        }
        address.set_default(becomesDefault);
        pickUpAddresses.add(address);
    }

    @DynamoDBIgnore
    public boolean makeDefaultPickUpAddress(String id) {
        if (findPickUpAddress(id).isEmpty()) {
            return false;
        }
        pickUpAddresses.forEach(address -> address.set_default(id.equals(address.getId())));
        return true;
    }

    /** Removing the default address hands the flag to the first remaining one. */
    @DynamoDBIgnore
    public boolean removePickUpAddress(String id) {
        boolean removed = pickUpAddresses.removeIf(address -> id.equals(address.getId()));
        if (removed && !pickUpAddresses.isEmpty() && pickUpAddresses.stream().noneMatch(ShippingDetails::is_default)) {
            pickUpAddresses.getFirst().set_default(true);
        }
        return removed;
    }

    /**
     * The sender printed on labels: only the default sender address is ever read ({@code ShippingService}), falling
     * back to the pickup address of the shipment. Null means "the same as the pickup address".
     */
    @DynamoDBIgnore
    public ShippingDetails getLabelSender() {
        return getDefaultSenderAddress().orElse(null);
    }

    /** Keeps a single, default sender address, or none to print the pickup address; unused entries are dropped. */
    @DynamoDBIgnore
    public void setLabelSender(ShippingDetails sender) {
        senderAddresses = new LinkedList<>();
        if (sender != null) {
            if (isBlank(sender.getId())) {
                sender.setId(UUID.randomUUID().toString());
            }
            sender.set_default(true);
            senderAddresses.add(sender);
        }
    }

    @DynamoDBIgnore
    public Optional<PackageTemplate> findPackageTemplate(String id) {
        return packageTemplates.stream().filter(template -> id.equals(template.getId())).findFirst();
    }

    /** The first template of the store becomes its default, as does one added with {@code makeDefault}. */
    @DynamoDBIgnore
    public void addPackageTemplate(PackageTemplate template, boolean makeDefault) {
        if (isBlank(template.getId())) {
            template.setId(UUID.randomUUID().toString());
        }
        boolean becomesDefault = makeDefault || packageTemplates.stream().noneMatch(PackageTemplate::isDefault);
        if (becomesDefault) {
            packageTemplates.forEach(other -> other.setDefault(false));
        }
        template.setDefault(becomesDefault);
        packageTemplates.add(template);
    }

    @DynamoDBIgnore
    public boolean makeDefaultPackageTemplate(String id) {
        if (findPackageTemplate(id).isEmpty()) {
            return false;
        }
        packageTemplates.forEach(template -> template.setDefault(id.equals(template.getId())));
        return true;
    }

    /** Removing the default template hands the flag to the first remaining one. */
    @DynamoDBIgnore
    public boolean removePackageTemplate(String id) {
        boolean removed = packageTemplates.removeIf(template -> id.equals(template.getId()));
        if (removed && !packageTemplates.isEmpty() && packageTemplates.stream().noneMatch(PackageTemplate::isDefault)) {
            packageTemplates.getFirst().setDefault(true);
        }
        return removed;
    }

    /** Old records without an id get one, so they can be addressed by URL; true when anything was assigned. */
    @DynamoDBIgnore
    public boolean assignMissingIds() {
        boolean changed = false;
        for (ShippingDetails address : pickUpAddresses) {
            if (isBlank(address.getId())) {
                address.setId(UUID.randomUUID().toString());
                changed = true;
            }
        }
        for (PackageTemplate template : packageTemplates) {
            if (isBlank(template.getId())) {
                template.setId(UUID.randomUUID().toString());
                changed = true;
            }
        }
        return changed;
    }
}
