package pl.commercelink.web.dtos;

import pl.commercelink.inventory.deliveries.DeliveryItem;

import java.util.Collections;

public class SuggestedDeliveryItem {

    private String category;
    private String name;
    private String ean;
    private String mfn;
    private int expectedQty;
    private int availableAtSupplier;
    private boolean lowestPrice;
    private double unitCost;
    private int requestedQty;

    public SuggestedDeliveryItem() {
    }

    public static SuggestedDeliveryItem of(String category, String name, String ean, String mfn,
                                           int expectedQty, int availableAtSupplier, boolean lowestPrice, double unitCost) {
        SuggestedDeliveryItem item = new SuggestedDeliveryItem();
        item.category = category;
        item.name = name;
        item.ean = ean;
        item.mfn = mfn;
        item.expectedQty = expectedQty;
        item.availableAtSupplier = availableAtSupplier;
        item.lowestPrice = lowestPrice;
        item.unitCost = unitCost;
        item.requestedQty = 0;
        return item;
    }

    public DeliveryItem toDeliveryItem() {
        DeliveryItem item = new DeliveryItem(name, ean, mfn, unitCost, Collections.emptyList());
        item.setRequestedQty(requestedQty);
        return item;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public String getMfn() {
        return mfn;
    }

    public void setMfn(String mfn) {
        this.mfn = mfn;
    }

    public int getExpectedQty() {
        return expectedQty;
    }

    public void setExpectedQty(int expectedQty) {
        this.expectedQty = expectedQty;
    }

    public int getAvailableAtSupplier() {
        return availableAtSupplier;
    }

    public void setAvailableAtSupplier(int availableAtSupplier) {
        this.availableAtSupplier = availableAtSupplier;
    }

    public boolean isLowestPrice() {
        return lowestPrice;
    }

    public void setLowestPrice(boolean lowestPrice) {
        this.lowestPrice = lowestPrice;
    }

    public double getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(double unitCost) {
        this.unitCost = unitCost;
    }

    public int getRequestedQty() {
        return requestedQty;
    }

    public void setRequestedQty(int requestedQty) {
        this.requestedQty = requestedQty;
    }
}
