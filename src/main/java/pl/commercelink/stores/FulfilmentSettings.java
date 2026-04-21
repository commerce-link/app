package pl.commercelink.stores;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.taxonomy.ProductGroup;
import pl.commercelink.products.ProductGroupListConverter;

import java.util.List;

@DynamoDBDocument
public class FulfilmentSettings {

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
    @DynamoDBTypeConverted(converter = ProductGroupListConverter.class)
    private List<ProductGroup> enabledProductGroups;
    @DynamoDBAttribute(attributeName = "enabledProviders")
    private List<String> enabledProviders;

    public FulfilmentSettings() {
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

    public List<ProductGroup> getEnabledProductGroups() {
        return enabledProductGroups;
    }

    public void setEnabledProductGroups(List<ProductGroup> enabledProductGroups) {
        this.enabledProductGroups = enabledProductGroups;
    }

    public List<String> getEnabledProviders() {
        return enabledProviders;
    }

    public void setEnabledProviders(List<String> enabledProviders) {
        this.enabledProviders = enabledProviders;
    }
}
