package pl.commercelink.orders.fulfilment;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AutomatedOrderFulfilment extends OrderFulfilment {

    private final Inventory inventory;

    public AutomatedOrderFulfilment(Inventory inventory, OrdersRepository ordersRepository, OrderLifecycle orderLifecycle, OrderItemsRepository orderItemsRepository, WarehouseFulfilmentService warehouseFulfilmentService) {
        super(ordersRepository, orderItemsRepository, orderLifecycle, warehouseFulfilmentService);

        this.inventory = inventory;
    }

    public void run(String storeId, List<OrderItem> orderItems) {
        FulfilmentGroupsGenerator generator = FulfilmentGroupsGenerator.builder()
                .withInventory(inventory.withWarehouseDataOnly(storeId))
                .withAcceptedSupplier(SupplierRegistry.WAREHOUSE)
                .build();

        List<FulfilmentItem> fulfilmentItems = generator.run(orderItems);

        List<OrderItem> acceptedOrderItems = orderItems.stream()
                .map(i -> accept(i, fulfilmentItems))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        super.commit(storeId, acceptedOrderItems);
    }

}
