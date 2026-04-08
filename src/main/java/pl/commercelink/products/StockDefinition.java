package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.warehouse.StockLevel;

@DynamoDBDocument
public class StockDefinition {

    @DynamoDBAttribute(attributeName = "criticalStockThreshold")
    private int criticalStockThreshold;
    @DynamoDBAttribute(attributeName = "lowStockThreshold")
    private int lowStockThreshold;
    @DynamoDBAttribute(attributeName = "highStockThreshold")
    private int highStockThreshold;

    // required by DynamoDB
    public StockDefinition() {
    }

    public StockDefinition(int criticalStockThreshold, int lowStockThreshold, int highStockThreshold) {
        this.criticalStockThreshold = criticalStockThreshold;
        this.lowStockThreshold = lowStockThreshold;
        this.highStockThreshold = highStockThreshold;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return criticalStockThreshold > 0 && lowStockThreshold >= criticalStockThreshold && highStockThreshold >= lowStockThreshold;
    }

    public StockLevel getStockLevel(MatchedInventory matchedInventory) {
        long noOfDistributorsWithProduct = matchedInventory.getNoOfSuppliersWithProduct(SupplierType.Distributor);
        long totalAvailableDistributorsQty = matchedInventory.getTotalAvailableQty(SupplierType.Distributor);
        long noOfRetailersWithProduct = matchedInventory.getNoOfSuppliersWithProduct(SupplierType.Retailer);
        long totalAvailableRetailerQty = matchedInventory.getTotalAvailableQty(SupplierType.Retailer);

        if (matchedInventory.hasOffersFrom(SupplierRegistry.WAREHOUSE)) {
            if (noOfRetailersWithProduct > 0 || noOfDistributorsWithProduct > 1) {
                return StockLevel.High;
            }
            return StockLevel.Medium;
        }

        if (matchedInventory.hasOffersFromLocalSuppliers()) {

            if (noOfDistributorsWithProduct >= 3 || totalAvailableDistributorsQty >= highStockThreshold || totalAvailableRetailerQty >= highStockThreshold) {
                return StockLevel.High;
            }

            if (noOfDistributorsWithProduct == 0) {
                if (totalAvailableRetailerQty <= criticalStockThreshold) {
                    return StockLevel.Critical;
                }
                return totalAvailableRetailerQty <= lowStockThreshold ? StockLevel.Low : StockLevel.Medium;
            }

            if (noOfRetailersWithProduct == 0) {
                if (totalAvailableDistributorsQty <= criticalStockThreshold) {
                    return StockLevel.Critical;
                }
                return totalAvailableDistributorsQty <= lowStockThreshold ? StockLevel.Low : StockLevel.Medium;
            }

            if (totalAvailableDistributorsQty <= lowStockThreshold && totalAvailableRetailerQty <= lowStockThreshold) {
                return (totalAvailableDistributorsQty <= criticalStockThreshold && totalAvailableRetailerQty <= criticalStockThreshold) ? StockLevel.Critical : StockLevel.Low;
            }

            return StockLevel.Medium;

        } else {

            if (noOfDistributorsWithProduct >= 2 && totalAvailableDistributorsQty >= lowStockThreshold) {
                return StockLevel.Low;
            }

            return StockLevel.Critical;

        }
    }

    // required by DynamoDB
    public int getCriticalStockThreshold() {
        return criticalStockThreshold;
    }

    public void setCriticalStockThreshold(int criticalStockThreshold) {
        this.criticalStockThreshold = criticalStockThreshold;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public int getHighStockThreshold() {
        return highStockThreshold;
    }

    public void setHighStockThreshold(int highStockThreshold) {
        this.highStockThreshold = highStockThreshold;
    }
}
