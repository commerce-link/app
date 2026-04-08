package pl.commercelink.baskets;

import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.LinkedList;
import java.util.List;

public class OfferItem {

    private int sequenceNumber;
    private BasketItem basketItem;

    private double lowestCost;
    private int lowestCostQty;

    private double medianCost;
    private String medianCostProviders = "";
    private int medianCostQty;

    private String remainingProviders = "";
    private long totalQty;

    private boolean existInInventory;

    public OfferItem(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
        this.existInInventory = false;
    }

    public OfferItem(int sequenceNumber, BasketItem basketItem) {
        this.sequenceNumber = sequenceNumber;
        this.basketItem = basketItem;
        this.existInInventory = false;
    }

    public OfferItem(int sequenceNumber, BasketItem basketItem, MatchedInventory matchedInventory) {
        this.basketItem = basketItem;
        this.sequenceNumber = sequenceNumber;

        this.lowestCost = matchedInventory.getLowestPrice().grossValue();
        this.lowestCostQty = matchedInventory.getLowestPricedInventoryItem().qty();

        this.medianCost = matchedInventory.getMedianPrice().grossValue();
        this.medianCostQty = matchedInventory.getMedianAvailableQty();
        this.medianCostProviders = String.join(", ", matchedInventory.getMedianPriceSuppliers());

        this.totalQty = matchedInventory.getTotalAvailableQty();

        List<String> providers = new LinkedList<>(matchedInventory.getSuppliers());
        providers.removeAll(matchedInventory.getMedianPriceSuppliers());
        this.remainingProviders = String.join(", ", providers);

        this.existInInventory = true;
    }

    public boolean isComplete() {
        return basketItem != null && lowestCost >= 0 && lowestCostQty >= 0 && totalQty >= 0;
    }

    public BasketItem getBasketItem() { return basketItem; }

    public int getSequenceNumber() { return sequenceNumber; }

    public String getCostRangeDistribution() {
        if (basketItem.getCategory() == ProductCategory.Services) {
            return "";
        }

        return String.format("%.2f zł (min) → %.2f zł (med)", lowestCost, medianCost);
    }

    public String getQtyDistribution() {
        if (basketItem.getCategory() == ProductCategory.Services) {
            return "";
        }

        return String.format("%d szt. (min) • %d szt. (med) • %d szt. (total)", lowestCostQty, medianCostQty, totalQty);
    }

    public String getSuppliersDistribution() {
        if (basketItem.getCategory() == ProductCategory.Services) {
            return "";
        }

        return String.format("%s | %s", medianCostProviders, remainingProviders);
    }

    public boolean isExistInInventory() { return existInInventory; }

    public double getTotalPrice() { return basketItem.getTotalPrice(); }

    public double getTotalCost() { return basketItem.getTotalCost(); }

    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
}
