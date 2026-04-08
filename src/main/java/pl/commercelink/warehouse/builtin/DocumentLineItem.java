package pl.commercelink.warehouse.builtin;

import pl.commercelink.warehouse.api.GoodsOutItem;
import pl.commercelink.warehouse.api.GoodsReceiptItem;

public class DocumentLineItem {
    private final String deliveryId;
    private final String ean;
    private final String mfn;
    private final String name;
    private final int qty;
    private final double unitPrice;

    public DocumentLineItem(String deliveryId, String ean, String mfn, String name, int qty, double unitPrice) {
        this.deliveryId = deliveryId;
        this.ean = ean;
        this.mfn = mfn;
        this.name = name;
        this.qty = qty;
        this.unitPrice = unitPrice;
    }

    public static DocumentLineItem from(WarehouseItem item) {
        return new DocumentLineItem(
                item.getDeliveryId(),
                item.getEan(),
                item.getManufacturerCode(),
                item.getName(),
                item.getQty(),
                item.getCost()
        );
    }

    public static DocumentLineItem from(GoodsReceiptItem item) {
        return new DocumentLineItem(
                item.getDeliveryId(),
                item.getEan(),
                item.getMfn(),
                item.getName(),
                item.getQty(),
                item.getUnitPrice()
        );
    }

    public static DocumentLineItem from(GoodsOutItem item) {
        return new DocumentLineItem(
                item.getDeliveryId(),
                item.getEan(),
                item.getMfn(),
                item.getName(),
                item.getQty(),
                item.getUnitPrice()
        );
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

    public int getQty() {
        return qty;
    }

    public double getUnitPrice() {
        return unitPrice;
    }
}
