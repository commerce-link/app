package pl.commercelink.warehouse.api;

public class GoodsOutItem {
    private final String deliveryId;
    private final String ean;
    private final String mfn;
    private final String name;
    private final int qty;
    private final double unitPrice;
    private final double tax;

    public GoodsOutItem(String deliveryId, String ean, String mfn, String name, int qty, double unitPrice, double tax) {
        this.deliveryId = deliveryId;
        this.ean = ean;
        this.mfn = mfn;
        this.name = name;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.tax = tax;
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

    public double getTax() {
        return tax;
    }
}
