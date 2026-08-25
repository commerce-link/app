package pl.commercelink.orders.fulfilment;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.warehouse.WarehouseFulfilmentService;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

@Component
public class ManualWarehouseItemFulfilment extends OrderFulfilment {

    public ManualWarehouseItemFulfilment(OrdersRepository ordersRepository, OrderLifecycle orderLifecycle, OrderItemsRepository orderItemsRepository, WarehouseFulfilmentService warehouseFulfilmentService) {
        super(ordersRepository, orderItemsRepository, orderLifecycle, warehouseFulfilmentService);
    }

    public void run(String storeId, OrderItem orderItem, WarehouseItemView warehouseItem) {
        orderItem.addFulfilment(new FulfilmentSource(orderItem, warehouseItem.toInventoryItem()));
        orderItem.requestWarehouseItem(warehouseItem.getItemId());

        super.commit(storeId, List.of(orderItem));
    }
}
