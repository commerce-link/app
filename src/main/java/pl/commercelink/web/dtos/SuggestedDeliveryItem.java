package pl.commercelink.web.dtos;

import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Collections;

public class SuggestedDeliveryItem {

    private ProductCategory category;
    private String name;
    private String ean;
    private String mfn;
    private int expectedQty;
    private double unitCost;
    private int requestedQty;

    public SuggestedDeliveryItem() {
    }

    public static SuggestedDeliveryItem of(ProductCategory category, String name, String ean, String mfn,
                                           int expectedQty, double unitCost) {
        SuggestedDeliveryItem item = new SuggestedDeliveryItem();
        item.category = category;
        item.name = name;
        item.ean = ean;
        item.mfn = mfn;
        item.expectedQty = expectedQty;
        item.unitCost = unitCost;
        item.requestedQty = 0;
        return item;
    }

    public DeliveryItem toDeliveryItem() {
        DeliveryItem item = new DeliveryItem(name, ean, mfn, unitCost, Collections.emptyList());
        item.setRequestedQty(requestedQty);
        return item;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
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
