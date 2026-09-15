package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;

@DynamoDBDocument
public class StoreSupplierConnection {

    @DynamoDBAttribute(attributeName = "supplierName")
    private String supplierName;

    @DynamoDBAttribute(attributeName = "mode")
    @DynamoDBTypeConvertedEnum
    private ConnectionMode mode = ConnectionMode.GLOBAL;

    @DynamoDBAttribute(attributeName = "includeInPricing")
    private boolean includeInPricing = true;

    @DynamoDBAttribute(attributeName = "includeInFulfilment")
    private boolean includeInFulfilment = true;

    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled = true;

    @DynamoDBAttribute(attributeName = "externalSupplierId")
    private String externalSupplierId;
    @DynamoDBAttribute(attributeName = "feedSchedule")
    private String feedSchedule;

    @DynamoDBAttribute(attributeName = "label")
    private String label;

    @DynamoDBAttribute(attributeName = "billingShortcut")
    private String billingShortcut;

    public StoreSupplierConnection() {
    }

    public StoreSupplierConnection(String supplierName, ConnectionMode mode) {
        this(supplierName, mode, true, true);
    }

    public StoreSupplierConnection(String supplierName, ConnectionMode mode,
                                   boolean includeInPricing, boolean includeInFulfilment) {
        this.supplierName = supplierName;
        this.mode = mode;
        this.includeInPricing = includeInPricing;
        this.includeInFulfilment = includeInFulfilment;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public ConnectionMode getMode() {
        return mode;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }

    public boolean isIncludeInPricing() {
        return includeInPricing;
    }

    public void setIncludeInPricing(boolean includeInPricing) {
        this.includeInPricing = includeInPricing;
    }

    public boolean isIncludeInFulfilment() {
        return includeInFulfilment;
    }

    public void setIncludeInFulfilment(boolean includeInFulfilment) {
        this.includeInFulfilment = includeInFulfilment;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExternalSupplierId() {
        return externalSupplierId;
    }

    // A GLOBAL connection is the platform's shared supplier, not a vendor of this store's own
    // marketplace, so it never takes part in marketplace routing. connectOrUpdate() never stores
    // an id on one; this keeps every reader consistent with that rule whatever is in the table.
    public String routingExternalSupplierId() {
        return mode == ConnectionMode.GLOBAL ? null : externalSupplierId;
    }

    public void setExternalSupplierId(String externalSupplierId) {
        this.externalSupplierId = externalSupplierId;
    }

    public String getFeedSchedule() {
        return feedSchedule;
    }

    public void setFeedSchedule(String feedSchedule) {
        this.feedSchedule = feedSchedule;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBillingShortcut() {
        return billingShortcut;
    }

    public void setBillingShortcut(String billingShortcut) {
        this.billingShortcut = billingShortcut;
    }
}
