package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.ItemType;

import java.util.Objects;

public class FulfilmentSource {

    private String name;
    private int sequenceNumber;
    private ItemType itemType;

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
        this.sequenceNumber = orderItem.getSequenceNumber();
        this.itemType = orderItem.getItemType();

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
        return qty == that.qty && Double.compare(priceNet, that.priceNet) == 0 && Double.compare(priceGross, that.priceGross) == 0 && Objects.equals(provider, that.provider) && Objects.equals(ean, that.ean) && Objects.equals(mfn, that.mfn) && sequenceNumber == that.sequenceNumber && itemType == that.itemType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, ean, mfn, sequenceNumber, itemType, qty, priceNet, priceGross);
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

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public boolean isService() {
        return itemType == ItemType.SERVICE;
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
