package pl.commercelink.inventory.deliveries;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Item;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

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

    /**
     * Where the item's delivery link of an order leads. dropship is the order's DropshipEligibility assessment: a
     * direct-to-consumer item goes to the order's dropship page only when its supplier is one the assessment accepts,
     * exactly the split DeliveriesPlanningService makes; any other supplier's items travel the ordinary warehouse
     * route, so the link leads to that supplier's delivery planning like on a warehouse order.
     */
    public String resolveFor(Order order, OrderItem item, DropshipAssessment dropship) {
        if (isAwaitingDropshipDelivery(order, item) && dropship.supports(item.getDeliveryId())) {
            return dropshipCreateLink(order.getOrderId(), item.getDeliveryId());
        }
        return resolveFor(item);
    }

    /** The new-delivery page of one order's dropship lines at a supplier, opened from the order (back leads there). */
    public static String dropshipCreateLink(String orderId, String provider) {
        return "/dashboard/deliveries/create/" + UriUtils.encodePathSegment(provider, StandardCharsets.UTF_8)
                + "?order=" + UriUtils.encodeQueryParam(orderId, StandardCharsets.UTF_8) + "&from=order";
    }

    /**
     * Any new-delivery page, a warehouse batch or an order's dropship lines, in its store or super admin variant: the
     * pages only an admin (DeliveryCreateController) opens.
     */
    public static boolean isCreateLink(String href) {
        return href != null && href.contains("/deliveries/create/");
    }

    public static boolean isDropshipCreateLink(String href) {
        return href != null && href.startsWith("/dashboard/deliveries/create/") && href.contains("?order=");
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
