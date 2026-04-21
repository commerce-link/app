package pl.commercelink.warehouse.api;

import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.taxonomy.ProductCategory;

public class GoodsReceiptItem {

    private final String deliveryId;
    private final String ean;
    private final String mfn;
    private final String name;
    private final ProductCategory category;
    private final int qty;
    private final double unitPrice;
    private final double tax;
    private final String serialNo;

    private GoodsReceiptItem(String deliveryId, String ean, String mfn, String name, ProductCategory category, int qty, double unitPrice, double tax, String serialNo) {
        this.deliveryId = deliveryId;
        this.ean = ean;
        this.mfn = mfn;
        this.name = name;
        this.category = category;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.tax = tax;
        this.serialNo = serialNo;
    }

    public static GoodsReceiptItem from(RMAItem rmaItem) {
        return new GoodsReceiptItem(
                rmaItem.getDeliveryId(),
                rmaItem.getEan(),
                rmaItem.getMfn(),
                rmaItem.getName(),
                ProductCategory.Other,
                rmaItem.getQty(),
                rmaItem.getCost(),
                rmaItem.getTax(),
                rmaItem.getSerialNo()
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
}
