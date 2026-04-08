package pl.commercelink.orders.fulfilment;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.*;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ManualOrderFulfilment extends OrderFulfilment {

    private final Inventory inventory;
    private final SupplierRegistry supplierRegistry;

    public ManualOrderFulfilment(Inventory inventory, OrdersRepository ordersRepository, OrderLifecycle orderLifecycle, OrderItemsRepository orderItemsRepository, WarehouseFulfilmentService warehouseFulfilmentService, SupplierRegistry supplierRegistry) {
        super(ordersRepository, orderItemsRepository, orderLifecycle, warehouseFulfilmentService);
        this.inventory = inventory;
        this.supplierRegistry = supplierRegistry;
    }

    public FulfilmentForm init(String storeId, List<String> selectedOrders, FulfilmentPathSelector pathSelector, boolean isSuperAdmin) {
        String redirectUrl = isSuperAdmin ? "redirect:/dashboard/fulfilment/queue" : "redirect:/dashboard/orders";

        List<OrderItem> orderItems = selectedOrders.stream()
                .flatMap(orderId -> orderItemsRepository
                        .findByOrderIdAndStatus(orderId, FulfilmentStatus.New).stream()
                )
                .collect(Collectors.toList());

        if (orderItems.isEmpty()) {
            return new FulfilmentForm("orders", redirectUrl, new LinkedList<>());
        }

        FulfilmentGroupsGenerator.Builder builder = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory.withEnabledSuppliersAndWarehouseData(storeId));
        if (orderItems.stream().map(OrderItem::getOrderId).filter(StringUtils::isNotBlank).distinct().count() > 1) {
            // in the case of multiple orders show only options that can satisfy demand
            builder.withCompleteFulfilmentOnly();
        }
        List<FulfilmentGroup> entries = builder.build().runWithGrouping(orderItems);

        if (pathSelector.requiresPathCalculation()) {
            List<FulfilmentPath> paths = new FulfilmentPathFinder(supplierRegistry).resolve(entries);
            pathSelector.select(paths).ifPresent(p -> p.accept(entries));
        }

        return new FulfilmentForm("orders", redirectUrl, selectedOrders, entries);
    }

    public void commit(String storeId, FulfilmentForm form) {
        Map<String, List<FulfilmentItem>> entriesByOrderId = form.getAcceptedFulfilmentItemsGroupedByOrderId();

        for (String orderId : entriesByOrderId.keySet()) {
            List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId)
                    .stream()
                    .map(i -> accept(i, entriesByOrderId.get(orderId)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            super.commit(storeId, orderItems);
        }
    }
}
