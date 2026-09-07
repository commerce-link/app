package pl.commercelink.inventory.deliveries;

import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Item;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class DeliveryRedirectResolver {

    public String resolveFor(Item item) {
        if (SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId())) {
            return "/dashboard/warehouse";
        }
        if (isAwaitingDelivery(item)) {
            return "/dashboard/deliveries/create/" + item.getDeliveryId();
        }
        return "/dashboard/deliveries/details?deliveryId=" + item.getDeliveryId();
    }

    public String resolveFor(Order order, OrderItem item) {
        if (isAwaitingDropshipDelivery(order, item)) {
            return "/dashboard/orders/" + order.getOrderId() + "/dropship?provider="
                    + URLEncoder.encode(item.getDeliveryId(), StandardCharsets.UTF_8);
        }
        return resolveFor(item);
    }

    private boolean isAwaitingDropshipDelivery(Order order, Item item) {
        return order.getFulfilmentType() == FulfilmentType.DirectToConsumer
                && isAwaitingDelivery(item)
                && !SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId());
    }

    private boolean isAwaitingDelivery(Item item) {
        return item.hasOneOfTheStatuses(FulfilmentStatus.New, FulfilmentStatus.Allocation);
    }
}
