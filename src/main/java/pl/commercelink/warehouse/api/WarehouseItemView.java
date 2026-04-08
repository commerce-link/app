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

    public WarehouseItemView(
            String storeId,
            String itemId,
            String ean,
            String mfn,
            Price price,
            int qty,
            FulfilmentStatus status
    ) {
        this.storeId = storeId;
        this.itemId = itemId;
        this.ean = ean;
        this.mfn = mfn;
        this.price = price;
        this.qty = qty;
        this.status = status;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getQty() {
        return qty;
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
