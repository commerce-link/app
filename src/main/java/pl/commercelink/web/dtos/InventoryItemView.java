package pl.commercelink.web.dtos;

import pl.commercelink.inventory.supplier.manual.ManualSupplierInfos;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.WarehouseItemView;

public record InventoryItemView(
        String supplier,
        String supplierLabel,
        String productEan,
        String productCode,
        double grossPrice,
        int qty,
        ItemCondition condition
) {

    public static InventoryItemView from(InventoryItem item) {
        return new InventoryItemView(
                item.supplier(),
                ManualSupplierInfos.label(item.supplier()),
                item.ean(),
                item.mfn(),
                Price.fromNet(item.netPrice()).grossValue(),
                item.qty(),
                null
        );
    }

    public static InventoryItemView from(WarehouseItemView item) {
        return new InventoryItemView(
                SupplierRegistry.WAREHOUSE,
                ManualSupplierInfos.label(SupplierRegistry.WAREHOUSE),
                item.getEan(),
                item.getMfn(),
                item.getPrice().grossValue(),
                item.getQty(),
                item.getCondition()
        );
    }

    public boolean hasSpecialCondition() {
        return condition != null && condition != ItemCondition.Sealed;
    }
}
