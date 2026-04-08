package pl.commercelink.orders.fulfilment;

import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.OrderItem;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static pl.commercelink.inventory.supplier.SupplierRegistry.WAREHOUSE;

public class FulfilmentItem {

    private boolean accepted;
    private FulfilmentAllocation allocation;
    private FulfilmentSource source;

    public static FulfilmentItem manual(OrderItem orderItem) {
        return new FulfilmentItem(orderItem, emptyInventoryItem(orderItem, SupplierRegistry.OTHER), false);
    }

    public static FulfilmentItem internal(OrderItem orderItem) {
        return new FulfilmentItem(orderItem, emptyInventoryItem(orderItem, SupplierRegistry.WAREHOUSE), true);
    }

    public static List<FulfilmentItem> fromInventory(OrderItem orderItem, Collection<InventoryItem> inventoryItems) {
        if (inventoryItems.isEmpty()) {
            return Collections.singletonList(manual(orderItem));
        }

        List<FulfilmentItem> fulfilmentItems = tryFulfilmentFromStock(orderItem, inventoryItems);

        if (fulfilmentItems.isEmpty()) {
            fulfilmentItems.addAll(tryFulfilmentFromDelivery(orderItem, inventoryItems));
        }

        if (fulfilmentItems.isEmpty()) {
            fulfilmentItems.addAll(tryFulfilmentFromProvider(orderItem, inventoryItems));
        }

        return fulfilmentItems;
    }

    private static List<FulfilmentItem> tryFulfilmentFromStock(OrderItem orderItem, Collection<InventoryItem> inventoryItems) {
        return inventoryItems.stream()
                .filter(i -> i.hasSupplier(WAREHOUSE))
                .filter(InventoryItem::inStock)
                .map(i -> new FulfilmentItem(orderItem, i, true))
                .collect(Collectors.toList());
    }

    private static List<FulfilmentItem> tryFulfilmentFromDelivery(OrderItem orderItem, Collection<InventoryItem> inventoryItems) {
        return inventoryItems.stream()
                .filter(i -> i.hasSupplier(WAREHOUSE))
                .filter(InventoryItem::inDelivery)
                .map(i -> new FulfilmentItem(orderItem, i, true))
                .collect(Collectors.toList());
    }

    private static List<FulfilmentItem> tryFulfilmentFromProvider(OrderItem orderItem, Collection<InventoryItem> inventoryItems) {
        return inventoryItems.stream()
                .filter(i -> !i.hasSupplier(WAREHOUSE))
                .map(i -> new FulfilmentItem(orderItem, i, false))
                .collect(Collectors.toList());
    }

    // used by UI
    public FulfilmentItem() {

    }

    public FulfilmentItem(FulfilmentAllocation allocation, FulfilmentSource source, boolean accepted) {
        this.allocation = allocation;
        this.source = source;
        this.accepted = accepted;
    }

    public FulfilmentItem(OrderItem orderItem, InventoryItem inventoryItem, boolean accepted) {
        this(new FulfilmentAllocation(orderItem), new FulfilmentSource(orderItem, inventoryItem), accepted);
    }

    public boolean hasProvider() {
        return !SupplierRegistry.OTHER.equalsIgnoreCase(source.getProvider());
    }

    public boolean isFor(OrderItem other) {
        return this.allocation.isFor(other);
    }

    public int getFirstSortNumber() {
        return source.getCategory().ordinal();
    }

    public int getSecondSortNumber() {
        return (int) source.getPriceNet();
    }

    public boolean isAccepted() {
        return accepted;
    }

    public FulfilmentSource getSource() {
        return source;
    }

    public void setSource(FulfilmentSource source) {
        this.source = source;
    }

    public FulfilmentAllocation getAllocation() {
        return allocation;
    }

    public void setAllocation(FulfilmentAllocation allocation) {
        this.allocation = allocation;
    }


    private static InventoryItem emptyInventoryItem(OrderItem anOrderItem, String providerName) {
        return new InventoryItem(null, null, anOrderItem.getCost(), "PLN", 1, 1, providerName);
    }

}
