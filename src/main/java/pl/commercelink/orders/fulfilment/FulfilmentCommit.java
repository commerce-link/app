package pl.commercelink.orders.fulfilment;

import pl.commercelink.orders.OrderItem;

import java.util.List;

/**
 * What a manual commit actually saved: products now waiting for a supplier delivery and products reserved from the
 * store's warehouse. Counted from the saved items' state, not from the posted form, which can name items that are no
 * longer open or that the marketplace routing refuses; a failed reservation and the unreserved rest of a partial one
 * are saved back as New and count as neither.
 */
public record FulfilmentCommit(long fromSuppliers, long fromWarehouse) {

    static FulfilmentCommit of(List<OrderItem> saved) {
        List<OrderItem> products = saved.stream().filter(OrderItem::isProduct).toList();
        return new FulfilmentCommit(
                products.stream().filter(OrderItem::isInAllocation).count(),
                products.stream().filter(OrderItem::isWarehouseFulfilled).count());
    }

    public boolean isEmpty() {
        return fromSuppliers == 0 && fromWarehouse == 0;
    }
}
