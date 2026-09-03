package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DropshipEligibility {

    private final DropshipPurchaseService dropshipPurchaseService;
    private final DeliveriesRepository deliveriesRepository;

    public DropshipAssessment assess(Order order, List<OrderItem> orderItems) {
        if (order.getFulfilmentType() != FulfilmentType.DirectToConsumer) {
            return DropshipAssessment.rejected(DropshipRejection.WAREHOUSE_FULFILMENT);
        }
        if (!order.hasShippingDetails()) {
            return DropshipAssessment.rejected(DropshipRejection.NO_SHIPPING_DETAILS);
        }
        List<OrderItem> allocated = orderItems.stream()
                .filter(OrderItem::isInAllocation)
                .toList();
        if (allocated.isEmpty()) {
            return DropshipAssessment.rejected(DropshipRejection.NOTHING_ALLOCATED);
        }

        // Items outside the allocation must already be accounted for. We happily ship from several suppliers at
        // once, but we will not fire a supplier purchase for an order that is not fully planned yet.
        List<OrderItem> otherItems = orderItems.stream()
                .filter(item -> !item.isInAllocation())
                .toList();
        Map<String, Delivery> deliveriesById = new HashMap<>();
        otherItems.stream()
                .filter(OrderItem::isWaitingForCollection)
                .map(OrderItem::getDeliveryId)
                .distinct()
                .forEach(deliveryId -> deliveriesById.put(deliveryId,
                        deliveriesRepository.findById(order.getStoreId(), deliveryId)));
        boolean everyOtherItemSettled = otherItems.stream()
                .allMatch(item -> isSettled(item, deliveriesById));
        if (!everyOtherItemSettled) {
            return DropshipAssessment.rejected(DropshipRejection.UNSETTLED_ITEMS);
        }

        // Each supplier that can ship straight to the customer becomes its own dropship delivery. Items sitting
        // at the warehouse, or at a supplier without dropshipping, travel the ordinary warehouse route.
        List<String> providers = allocated.stream()
                .map(OrderItem::getDeliveryId)
                .filter(provider -> !OrderItem.GENERIC_WAREHOUSE_ORDER_NO.equalsIgnoreCase(provider))
                .distinct()
                .filter(provider -> dropshipPurchaseService.isDropshipAvailable(order.getStoreId(), provider))
                .sorted()
                .toList();
        if (providers.isEmpty()) {
            return DropshipAssessment.rejected(DropshipRejection.NO_DROPSHIP_CAPABLE_SUPPLIER);
        }
        return DropshipAssessment.of(providers);
    }

    private boolean isSettled(OrderItem item, Map<String, Delivery> deliveriesById) {
        if (item.isDelivered()) {
            return true;
        }
        if (!item.isWaitingForCollection()) {
            return false;
        }
        Delivery delivery = deliveriesById.get(item.getDeliveryId());
        return delivery != null && delivery.isDropship();
    }
}
