package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.inventory.supplier.manual.ManualSupplierNames;

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

    @DynamoDBIgnore
    public boolean hasConsistentManualNaming() {
        return ManualSupplierNames.isManual(supplierName) == (mode == ConnectionMode.MANUAL);
    }
}
