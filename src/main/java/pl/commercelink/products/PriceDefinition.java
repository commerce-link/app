package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@DynamoDBDocument
public class PriceDefinition {

    public static final String DEFAULT_PRICING_GROUP = "Default";

    @DynamoDBAttribute(attributeName = "multiplier")
    private double multiplier;
    @DynamoDBAttribute(attributeName = "minProfit")
    private int minProfit;
    @DynamoDBAttribute(attributeName = "criticalStockPriceAdjustment")
    private int criticalStockPriceAdjustment;
    @DynamoDBAttribute(attributeName = "lowStockPriceAdjustment")
    private int lowStockPriceAdjustment;
    @DynamoDBAttribute(attributeName = "mediumStockPriceAdjustment")
    private int mediumStockPriceAdjustment;
    @DynamoDBAttribute(attributeName = "pricingGroup")
    private String pricingGroup;
    @DynamoDBAttribute(attributeName = "labelMatch")
    private String labelMatch;
    @DynamoDBAttribute(attributeName = "priceMatch")
    private double priceMatch;

    // required by DynamoDB
    public PriceDefinition() {
    }

    public PriceDefinition(double multiplier, int minProfit, int criticalStockPriceAdjustment, int lowStockPriceAdjustment, int mediumStockPriceAdjustment, String pricingGroup) {
        this.multiplier = multiplier;
        this.minProfit = minProfit;
        this.criticalStockPriceAdjustment = criticalStockPriceAdjustment;
        this.lowStockPriceAdjustment = lowStockPriceAdjustment;
        this.mediumStockPriceAdjustment = mediumStockPriceAdjustment;
        this.pricingGroup = pricingGroup;
    }

    public boolean hasPricingGroup(String other) {
        return pricingGroup.equalsIgnoreCase(other);
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return multiplier > 0 && minProfit >= 0 && criticalStockPriceAdjustment >= 0 && lowStockPriceAdjustment >= 0 && mediumStockPriceAdjustment >= 0 && isNotBlank(pricingGroup);
    }

    @DynamoDBIgnore
    public boolean matches(String label, double grossPrice) {
        if (isBlank(label) || isBlank(labelMatch)) {
            return false;
        }

        return label.trim().equalsIgnoreCase(labelMatch.trim()) && matches(grossPrice);
    }

    @DynamoDBIgnore
    private boolean matches(double grossPrice) {
        if (priceMatch > 0) {
            return grossPrice >= priceMatch;
        }
        return true;
    }

    // required by DynamoDB
    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public int getMinProfit() {
        return minProfit;
    }

    public void setMinProfit(int minProfit) {
        this.minProfit = minProfit;
    }

    public int getCriticalStockPriceAdjustment() {
        return criticalStockPriceAdjustment;
    }

    public void setCriticalStockPriceAdjustment(int criticalStockPriceAdjustment) {
        this.criticalStockPriceAdjustment = criticalStockPriceAdjustment;
    }

    public int getLowStockPriceAdjustment() {
        return lowStockPriceAdjustment;
    }

    public void setLowStockPriceAdjustment(int lowStockPriceAdjustment) {
        this.lowStockPriceAdjustment = lowStockPriceAdjustment;
    }

    public int getMediumStockPriceAdjustment() {
        return mediumStockPriceAdjustment;
    }

    public void setMediumStockPriceAdjustment(int mediumStockPriceAdjustment) {
        this.mediumStockPriceAdjustment = mediumStockPriceAdjustment;
    }

    public String getPricingGroup() {
        return pricingGroup;
    }

    public void setPricingGroup(String pricingGroup) {
        this.pricingGroup = pricingGroup;
    }

    public String getLabelMatch() {
        return labelMatch;
    }

    public void setLabelMatch(String labelMatch) {
        this.labelMatch = labelMatch;
    }

    public double getPriceMatch() {
        return priceMatch;
    }

    public void setPriceMatch(double priceMatch) {
        this.priceMatch = priceMatch;
    }
}
