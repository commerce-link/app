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

    /**
     * Whether the item's deliveryId is the id of a supplier delivery (claimed, ordered, delivered) rather than the
     * supplier it waits for (New, unclaimed Allocation) or the warehouse; the same split as the links above.
     */
    public boolean pointsToDelivery(Item item) {
        return item.getDeliveryId() != null && !SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId())
                && !isAwaitingDelivery(item);
    }

    private boolean isAwaitingDropshipDelivery(Order order, Item item) {
        return order.getFulfilmentType() == FulfilmentType.DirectToConsumer
                && isAwaitingDelivery(item)
                && !SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId());
    }

    private boolean isAwaitingDelivery(Item item) {
        // a claimed item is still in Allocation but already belongs to a delivery: its deliveryId is that
        // delivery's id, not a provider name, so the planning screen has nothing to show for it
        return item.hasOneOfTheStatuses(FulfilmentStatus.New, FulfilmentStatus.Allocation) && !item.isClaimed();
    }
}
