package pl.commercelink.warehouse.builtin;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.fulfilment.FulfilmentSource;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.TaxonomyResolver;
import pl.commercelink.taxonomy.TaxonomyResolver.ResolvedProduct;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ReservationRemovalItem;

@Component
class WarehouseItemFactory {

    private final TaxonomyResolver taxonomyResolver;

    WarehouseItemFactory(TaxonomyResolver taxonomyResolver) {
        this.taxonomyResolver = taxonomyResolver;
    }

    WarehouseItem create(String storeId, ReservationRemovalItem item) {
        ResolvedProduct resolved = taxonomyResolver.resolve(item.getMfn(), item.getName(), item.getCategory());
        WarehouseItem warehouseItem = new WarehouseItem(
                storeId, item.getDeliveryId(), resolved.category(), resolved.name(),
                item.getEan(), item.getMfn(), item.getUnitPrice(), item.getQty()
        );
        warehouseItem.setTax(item.getTax());
        warehouseItem.setSerialNo(item.getSerialNo());
        warehouseItem.setStatus(item.isDelivered() ? FulfilmentStatus.Delivered : FulfilmentStatus.Ordered);
        return warehouseItem;
    }

    WarehouseItem create(String storeId, GoodsReceiptItem item) {
        ResolvedProduct resolved = taxonomyResolver.resolve(item.getMfn(), item.getName(), item.getCategory());
        WarehouseItem warehouseItem = new WarehouseItem(
                storeId, item.getDeliveryId(), resolved.category(), resolved.name(),
                item.getEan(), item.getMfn(), item.getUnitPrice(), item.getQty()
        );
        warehouseItem.setTax(item.getTax());
        warehouseItem.setSerialNo(item.getSerialNo());
        warehouseItem.setStatus(FulfilmentStatus.Delivered);
        return warehouseItem;
    }

    WarehouseItem create(String storeId, FulfilmentItem item) {
        FulfilmentSource source = item.getSource();
        ResolvedProduct resolved = taxonomyResolver.resolve(source.getMfn(), source.getName(), source.getCategory());
        return new WarehouseItem(
                storeId,
                source.getProvider(),
                resolved.category(),
                resolved.name(),
                source.getEan(),
                source.getMfn(),
                source.getPriceNet(),
                Math.min(item.getAllocation().getOrderItemQty(), source.getQty())
        );
    }

    WarehouseItem create(String storeId, String provider, DeliveryItem item) {
        ResolvedProduct resolved = taxonomyResolver.resolve(item.getMfn(), item.getName(), ProductCategory.Other);
        return new WarehouseItem(
                storeId, provider, resolved.category(), resolved.name(),
                item.getEan(), item.getMfn(), item.getUnitCost(), item.getWarehouseQtyAdjustment()
        );
    }
}
