package pl.commercelink.warehouse.builtin;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveryItem;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.fulfilment.FulfilmentItem;
import pl.commercelink.orders.fulfilment.FulfilmentSource;
import pl.commercelink.products.ProductData;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.products.StoreCategoryResolver;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.warehouse.api.GoodsReceiptItem;
import pl.commercelink.warehouse.api.ReservationRemovalItem;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WarehouseItemFactory {

    private final ProductDataResolver productDataResolver;
    private final StoreCategoryResolver storeCategoryResolver;

    WarehouseItem create(String storeId, ReservationRemovalItem item) {
        ProductData product = productOf(storeId, item.getEan(), item.getName(), item.getCategory());
        WarehouseItem warehouseItem = new WarehouseItem(
                storeId, item.getDeliveryId(), categoryFor(storeId, product), nameOrUnknown(product.name()),
                item.getEan(), item.getMfn(), item.getUnitPrice(), item.getQty()
        );
        warehouseItem.setTax(item.getTax());
        warehouseItem.setSerialNo(item.getSerialNo());
        warehouseItem.setComment(item.getComment());
        warehouseItem.setStatus(item.isDelivered() ? FulfilmentStatus.Delivered : FulfilmentStatus.Ordered);
        warehouseItem.setCondition(item.getCondition());
        return warehouseItem;
    }

    WarehouseItem create(String storeId, GoodsReceiptItem item) {
        ProductData product = productOf(storeId, item.getEan(), item.getName(), item.getCategory());
        WarehouseItem warehouseItem = new WarehouseItem(
                storeId, item.getDeliveryId(), categoryFor(storeId, product), nameOrUnknown(product.name()),
                item.getEan(), item.getMfn(), item.getUnitPrice(), item.getQty()
        );
        warehouseItem.setTax(item.getTax());
        warehouseItem.setSerialNo(item.getSerialNo());
        warehouseItem.setStatus(FulfilmentStatus.Delivered);
        warehouseItem.setCondition(item.getCondition());
        return warehouseItem;
    }

    WarehouseItem create(String storeId, FulfilmentItem item) {
        FulfilmentSource source = item.getSource();
        ProductData product = productOf(storeId, source.getEan(), source.getName(), source.getCategory());
        return new WarehouseItem(
                storeId,
                source.getProvider(),
                categoryFor(storeId, product),
                nameOrUnknown(product.name()),
                source.getEan(),
                source.getMfn(),
                source.getPriceNet(),
                Math.min(item.getAllocation().getOrderItemQty(), source.getQty())
        );
    }

    WarehouseItem create(String storeId, String provider, DeliveryItem item) {
        return create(storeId, provider, item, item.getWarehouseQtyAdjustment());
    }

    WarehouseItem create(String storeId, String provider, DeliveryItem item, int qty) {
        ProductData product = productOf(storeId, item.getEan(), item.getName(), Categories.UNCATEGORIZED);
        return new WarehouseItem(
                storeId, provider, categoryFor(storeId, product), nameOrUnknown(product.name()),
                item.getEan(), item.getMfn(), item.getUnitCost(), qty
        );
    }

    private ProductData productOf(String storeId, String ean, String itemName, String itemCategory) {
        ProductData fromItem = new ProductData(itemName, null, itemCategory, null, null, null, null, null);
        return productDataResolver.ofEan(storeId, ean)
                .map(found -> found.orElse(fromItem))
                .orElse(fromItem);
    }

    private String categoryFor(String storeId, ProductData product) {
        return storeCategoryResolver.findCategoryName(storeId, product.categoryId())
                .orElse(product.category());
    }

    private static String nameOrUnknown(String name) {
        return Strings.isNotBlank(name) ? name : "Unknown";
    }
}
