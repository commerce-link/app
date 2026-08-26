package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;

@DynamoDBDocument
public class MarketplaceDefinition  {

    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "markup")
    private double markup;
    @DynamoDBAttribute(attributeName = "minDistributorsQty")
    private int minDistributorsQty;
    @DynamoDBAttribute(attributeName = "minQtyPerDistributor")
    private int minQtyPerDistributor;
    @DynamoDBAttribute(attributeName = "minNumOfDistributors")
    private int minNumOfDistributors;
    @DynamoDBAttribute(attributeName = "minNumOfLocalDistributors")
    private int minNumOfLocalDistributors;
    @DynamoDBAttribute(attributeName = "minWarehouseQty")
    private int minWarehouseQty;
    @DynamoDBAttribute(attributeName = "exportSelectedProducts")
    private boolean exportSelectedProducts = false;
    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled = true;

    // required by DynamoDB
    public MarketplaceDefinition() {
    }

    public MarketplaceDefinition(String name, double markup, int minDistributorsQty, int minQtyPerDistributor, int minNumOfDistributors, int minNumOfLocalDistributors, int minWarehouseQty) {
        this.name = name;
        this.markup = markup;
        this.minDistributorsQty = minDistributorsQty;
        this.minQtyPerDistributor = minQtyPerDistributor;
        this.minNumOfDistributors = minNumOfDistributors;
        this.minNumOfLocalDistributors = minNumOfLocalDistributors;
        this.minWarehouseQty = minWarehouseQty;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        boolean matchesBasicCriteria = name != null && markup > 0;
        return matchesBasicCriteria && (hasDistributorsCriteria() || hasWarehouseCriteria());
    }

    public long qtyToPublish(MatchedInventory inventory) {
        if (hasWarehouseCriteria()) {
            long warehouseQty = inventory.getTotalAvailableQtyFromSupplier(SupplierRegistry.WAREHOUSE);
            if (warehouseQty >= minWarehouseQty) {
                return warehouseQty;
            }
        }
        if (hasDistributorsCriteria() && meetsDistributorsCriteria(inventory)) {
            return inventory.getTotalAvailableQty();
        }
        return 0L;
    }

    private boolean meetsDistributorsCriteria(MatchedInventory inventory) {
        return inventory.hasOffersFromMultipleSuppliers(minNumOfDistributors, minQtyPerDistributor)
                && inventory.hasOffersFromMultipleLocalSuppliers(minNumOfLocalDistributors, minQtyPerDistributor)
                && inventory.hasTotalMinQty(minDistributorsQty);
    }

    private boolean hasDistributorsCriteria() {
        return minQtyPerDistributor > 0 && minNumOfDistributors > 0;
    }

    private boolean hasWarehouseCriteria() {
        return minWarehouseQty > 0;
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

    public int getMinDistributorsQty() {
        return minDistributorsQty;
    }

    public void setMinDistributorsQty(int minDistributorsQty) {
        this.minDistributorsQty = minDistributorsQty;
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

    public int getMinNumOfLocalDistributors() {
        return minNumOfLocalDistributors;
    }

    public void setMinNumOfLocalDistributors(int minNumOfLocalDistributors) {
        this.minNumOfLocalDistributors = minNumOfLocalDistributors;
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
