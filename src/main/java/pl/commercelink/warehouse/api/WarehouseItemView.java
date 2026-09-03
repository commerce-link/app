package pl.commercelink.warehouse.api;

import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.invoicing.api.Price;

public class WarehouseItemView {

    private final String storeId;
    private final String itemId;
    private final String ean;
    private final String mfn;
    private final Price price;
    private final int qty;
    private final FulfilmentStatus status;
    private final ItemCondition condition;

    public WarehouseItemView(
            String storeId,
            String itemId,
            String ean,
            String mfn,
            Price price,
            int qty,
            FulfilmentStatus status,
            ItemCondition condition
    ) {
        this.storeId = storeId;
        this.itemId = itemId;
        this.ean = ean;
        this.mfn = mfn;
        this.price = price;
        this.qty = qty;
        this.status = status;
        this.condition = condition;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getItemId() {
        return itemId;
    }

    public String getEan() {
        return ean;
    }

    public String getMfn() {
        return mfn;
    }

    public Price getPrice() {
        return price;
    }

    public int getQty() {
        return qty;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public boolean isSealed() {
        return condition == ItemCondition.Sealed;
    }

    public boolean isInStock() {
        return status == FulfilmentStatus.Delivered;
    }

    public boolean isInDelivery() {
        return status == FulfilmentStatus.Ordered;
    }

    public InventoryItem toInventoryItem() {
        return new InventoryItem(
                ean,
                mfn,
                price.netValue(),
                "PLN",
                qty,
                1,
                SupplierRegistry.WAREHOUSE,
                true,
                isInStock(),
                isInDelivery()
        );
    }
}
