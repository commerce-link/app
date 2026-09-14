package pl.commercelink.baskets;

import pl.commercelink.inventory.MatchedInventory;
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

    public OfferItem() {
        this.existInInventory = false;
    }

    public OfferItem(BasketItem basketItem) {
        this.basketItem = basketItem;
        this.existInInventory = false;
    }

    public OfferItem(BasketItem basketItem, MatchedInventory matchedInventory) {
        this.basketItem = basketItem;

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

    public int getPosition() {
        return basketItem == null ? Integer.MAX_VALUE : basketItem.getPosition();
    }

    public double getUnitPrice() {
        return basketItem == null ? 0 : basketItem.getUnitPrice();
    }

    public String getCostRange() {
        if (basketItem.isService()) {
            return "";
        }

        return String.format("%.2f → %.2f zł", lowestCost, medianCost);
    }

    public String getQtyRange() {
        if (basketItem.isService()) {
            return "";
        }

        return String.format("%d · %d · %d szt.", lowestCostQty, medianCostQty, totalQty);
    }

    public String getMedianCostProviders() {
        return basketItem.isService() ? "" : medianCostProviders;
    }

    public String getRemainingProviders() {
        return basketItem.isService() ? "" : remainingProviders;
    }

    public String getVariantGroupId() {
        return basketItem == null ? null : basketItem.getVariantGroupId();
    }

    public boolean isExistInInventory() { return existInInventory; }

    public double getTotalPrice() { return basketItem.getTotalPrice(); }

    public double getTotalCost() { return basketItem.getTotalCost(); }

    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
}
