package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

@DynamoDBDocument
public class MarketplaceDefinition  {

    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "markup")
    private double markup;
    @DynamoDBAttribute(attributeName = "minTotalQty")
    private int minTotalQty;
    @DynamoDBAttribute(attributeName = "minQtyPerDistributor")
    private int minQtyPerDistributor;
    @DynamoDBAttribute(attributeName = "minNumOfDistributors")
    private int minNumOfDistributors;
    @DynamoDBAttribute(attributeName = "minWarehouseQty")
    private int minWarehouseQty;
    @DynamoDBAttribute(attributeName = "exportSelectedProducts")
    private boolean exportSelectedProducts = false;
    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled = true;

    // required by DynamoDB
    public MarketplaceDefinition() {
    }

    public MarketplaceDefinition(String name, double markup, int minTotalQty, int minQtyPerDistributor, int minNumOfDistributors, int minWarehouseQty) {
        this.name = name;
        this.markup = markup;
        this.minTotalQty = minTotalQty;
        this.minQtyPerDistributor = minQtyPerDistributor;
        this.minNumOfDistributors = minNumOfDistributors;
        this.minWarehouseQty = minWarehouseQty;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        boolean matchesBasicCriteria = name != null && markup > 0;
        boolean matchesDistributorsCriteria = minTotalQty > 0 && minQtyPerDistributor > 0 && minNumOfDistributors > 0;
        boolean matchesWarehouseCriteria = minWarehouseQty > 0;

        if (matchesBasicCriteria) {
            return matchesDistributorsCriteria || matchesWarehouseCriteria;
        }
        return false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getMarkup() {
        return markup;
    }

    public void setMarkup(double markup) {
        this.markup = markup;
    }

    public int getMinTotalQty() {
        return minTotalQty;
    }

    public void setMinTotalQty(int minTotalQty) {
        this.minTotalQty = minTotalQty;
    }

    public int getMinQtyPerDistributor() {
        return minQtyPerDistributor;
    }

    public void setMinQtyPerDistributor(int minQtyPerDistributor) {
        this.minQtyPerDistributor = minQtyPerDistributor;
    }

    public int getMinNumOfDistributors() {
        return minNumOfDistributors;
    }

    public void setMinNumOfDistributors(int minNumOfDistributors) {
        this.minNumOfDistributors = minNumOfDistributors;
    }

    public int getMinWarehouseQty() {
        return minWarehouseQty;
    }

    public void setMinWarehouseQty(int minWarehouseQty) {
        this.minWarehouseQty = minWarehouseQty;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isExportSelectedProducts() {
        return exportSelectedProducts;
    }

    public void setExportSelectedProducts(boolean exportSelectedProducts) {
        this.exportSelectedProducts = exportSelectedProducts;
    }
}
