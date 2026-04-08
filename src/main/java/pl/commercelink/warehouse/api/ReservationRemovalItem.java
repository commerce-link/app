package pl.commercelink.warehouse.api;

import pl.commercelink.orders.OrderItem;
import pl.commercelink.taxonomy.ProductCategory;

public class ReservationRemovalItem {

    private String deliveryId;
    private String ean;
    private String mfn;
    private String name;
    private ProductCategory category;
    private int qty;
    private double unitPrice;
    private double tax;
    private String serialNo;
    private boolean delivered;

    public static ReservationRemovalItem from(OrderItem orderItem) {
        ReservationRemovalItem item = new ReservationRemovalItem();
        item.deliveryId = orderItem.getDeliveryId();
        item.ean = orderItem.getEan();
        item.mfn = orderItem.getManufacturerCode();
        item.name = orderItem.getName();
        item.category = orderItem.getCategory();
        item.qty = orderItem.getQty();
        item.unitPrice = orderItem.getCost();
        item.tax = orderItem.getTax();
        item.serialNo = orderItem.getSerialNo();
        item.delivered = orderItem.isDelivered();
        return item;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEan() {
        return ean;
    }

    public String getMfn() {
        return mfn;
    }

    public String getName() {
        return name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public int getQty() {
        return qty;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getTax() {
        return tax;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public boolean isDelivered() {
        return delivered;
    }
}
