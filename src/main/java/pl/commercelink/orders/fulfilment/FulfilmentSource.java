package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.Objects;

public class FulfilmentSource {

    private String name;
    private ProductCategory category;

    private String provider;
    private String ean;
    private String mfn;
    private int qty;
    private double priceNet;
    private double priceGross;

    public FulfilmentSource() {

    }

    public FulfilmentSource(OrderItem orderItem, InventoryItem inventoryItem) {
        this.name = orderItem.getName();
        this.category = orderItem.getCategory();

        this.provider = inventoryItem.supplier();
        this.ean = inventoryItem.ean();
        this.mfn = inventoryItem.mfn();
        this.qty = inventoryItem.qty();
        this.priceNet = inventoryItem.netPrice();
        this.priceGross = Price.fromNet(inventoryItem.netPrice()).grossValue();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FulfilmentSource that = (FulfilmentSource) o;
        return qty == that.qty && Double.compare(priceNet, that.priceNet) == 0 && Double.compare(priceGross, that.priceGross) == 0 && Objects.equals(provider, that.provider) && Objects.equals(ean, that.ean) && Objects.equals(mfn, that.mfn) && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, ean, mfn, category, qty, priceNet, priceGross);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public int getQty() {
        return qty;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public double getPriceNet() {
        return priceNet;
    }

    public void setPriceNet(double priceNet) {
        this.priceNet = priceNet;
    }

    public double getPriceGross() {
        return priceGross;
    }

    public void setPriceGross(double priceGross) {
        this.priceGross = priceGross;
    }
}
