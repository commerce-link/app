package pl.commercelink.web.dtos;

import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.warehouse.RestockPriceCategory;
import pl.commercelink.warehouse.RestockSuggestion;

import java.util.Collections;

public class SuggestedDeliveryItem {

    private String category;
    private String name;
    private String ean;
    private String mfn;
    private int expectedQty;
    private int availableAtSupplier;
    private RestockPriceCategory priceCategory;
    private double unitCost;
    private int requestedQty;

    public SuggestedDeliveryItem() {
    }

    public static SuggestedDeliveryItem from(RestockSuggestion suggestion) {
        SuggestedDeliveryItem item = new SuggestedDeliveryItem();
        item.category = suggestion.getCategory();
        item.name = suggestion.getName();
        item.ean = suggestion.getEan();
        item.mfn = suggestion.getOfferMfn();
        item.expectedQty = suggestion.getExpectedQuantity();
        item.availableAtSupplier = suggestion.getAvailableQty();
        item.priceCategory = suggestion.getPriceCategory();
        item.unitCost = suggestion.getNetPrice();
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

    public RestockPriceCategory getPriceCategory() {
        return priceCategory;
    }

    public void setPriceCategory(RestockPriceCategory priceCategory) {
        this.priceCategory = priceCategory;
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
