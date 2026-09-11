package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.ArrayList;
import java.util.List;

@DynamoDBDocument
public class FulfilmentConfiguration {

    @DynamoDBAttribute(attributeName = "orderAssemblyDays")
    private int orderAssemblyDays;
    @DynamoDBAttribute(attributeName = "orderRealizationDays")
    private int orderRealizationDays;
    @DynamoDBAttribute(attributeName = "automatedFulfilment")
    private boolean automatedFulfilment = false;
    @DynamoDBAttribute(attributeName = "defaultFulfilmentType")
    @DynamoDBTypeConvertedEnum
    private FulfilmentType defaultFulfilmentType = FulfilmentType.WarehouseFulfilment;
    @DynamoDBAttribute(attributeName = "enabledProductGroups")
    private List<String> enabledProductGroups;
    @DynamoDBAttribute(attributeName = "enabledCategories")
    private List<String> enabledCategories;
    @DynamoDBAttribute(attributeName = "canUseGlobalSuppliers")
    private boolean canUseGlobalSuppliers = false;
    @DynamoDBAttribute(attributeName = "supplierConnections")
    private List<StoreSupplierConnection> supplierConnections = new ArrayList<>();
    @DynamoDBAttribute(attributeName = "inventoryCacheTtlMinutes")
    private Integer inventoryCacheTtlMinutes;

    public FulfilmentConfiguration() {
    }

    public int getOrderRealizationDays() {
        return orderRealizationDays;
    }

    public void setOrderRealizationDays(int orderRealizationDays) {
        this.orderRealizationDays = orderRealizationDays;
    }

    public int getOrderAssemblyDays() {
        return orderAssemblyDays;
    }

    public void setOrderAssemblyDays(int orderAssemblyDays) {
        this.orderAssemblyDays = orderAssemblyDays;
    }

    public boolean isAutomatedFulfilment() {
        return automatedFulfilment;
    }

    public void setAutomatedFulfilment(boolean automatedFulfilment) {
        this.automatedFulfilment = automatedFulfilment;
    }

    public FulfilmentType getDefaultFulfilmentType() {
        return defaultFulfilmentType;
    }

    public void setDefaultFulfilmentType(FulfilmentType defaultFulfilmentType) {
        this.defaultFulfilmentType = defaultFulfilmentType;
    }

    public List<String> getEnabledProductGroups() {
        return enabledProductGroups;
    }

    public void setEnabledProductGroups(List<String> enabledProductGroups) {
        this.enabledProductGroups = enabledProductGroups;
    }

    public List<String> getEnabledCategories() {
        return enabledCategories;
    }

    public void setEnabledCategories(List<String> enabledCategories) {
        this.enabledCategories = enabledCategories;
    }

    public boolean isCanUseGlobalSuppliers() {
        return canUseGlobalSuppliers;
    }

    public void setCanUseGlobalSuppliers(boolean canUseGlobalSuppliers) {
        this.canUseGlobalSuppliers = canUseGlobalSuppliers;
    }

    public List<StoreSupplierConnection> getSupplierConnections() {
        return supplierConnections;
    }

    public void setSupplierConnections(List<StoreSupplierConnection> supplierConnections) {
        this.supplierConnections = supplierConnections;
    }

    public Integer getInventoryCacheTtlMinutes() {
        return inventoryCacheTtlMinutes;
    }

    public void setInventoryCacheTtlMinutes(Integer inventoryCacheTtlMinutes) {
        this.inventoryCacheTtlMinutes = inventoryCacheTtlMinutes;
    }

    /**
     * Returns a copy carrying the given connection list. Used by the per-supplier save paths: the
     * persister computes what changed by comparing the store's current configuration against the
     * submitted one, so the submitted one must be a separate object.
     */
    @DynamoDBIgnore
    public FulfilmentConfiguration withConnections(List<StoreSupplierConnection> connections) {
        FulfilmentConfiguration copy = new FulfilmentConfiguration();
        copy.setOrderAssemblyDays(orderAssemblyDays);
        copy.setOrderRealizationDays(orderRealizationDays);
        copy.setAutomatedFulfilment(automatedFulfilment);
        copy.setDefaultFulfilmentType(defaultFulfilmentType);
        copy.setEnabledProductGroups(enabledProductGroups);
        copy.setEnabledCategories(enabledCategories);
        copy.setCanUseGlobalSuppliers(canUseGlobalSuppliers);
        copy.setInventoryCacheTtlMinutes(inventoryCacheTtlMinutes);
        copy.setSupplierConnections(new ArrayList<>(connections));
        return copy;
    }
}
