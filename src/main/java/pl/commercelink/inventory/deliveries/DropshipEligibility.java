package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DropshipEligibility {

    private final DropshipPurchaseService dropshipPurchaseService;
    private final DeliveriesRepository deliveriesRepository;

    public Optional<String> eligibleProvider(Order order, List<OrderItem> orderItems) {
        if (order.getFulfilmentType() != FulfilmentType.DirectToConsumer || !order.hasShippingDetails()) {
            return Optional.empty();
        }
        List<OrderItem> allocated = orderItems.stream()
                .filter(OrderItem::isInAllocation)
                .toList();
        if (allocated.isEmpty()) {
            return Optional.empty();
        }
        Set<String> providers = allocated.stream()
                .map(OrderItem::getDeliveryId)
                .collect(Collectors.toSet());
        if (providers.size() != 1) {
            return Optional.empty();
        }
        String provider = providers.iterator().next();
        if (OrderItem.GENERIC_WAREHOUSE_ORDER_NO.equalsIgnoreCase(provider)) {
            return Optional.empty();
        }
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
            return Optional.empty();
        }
        return dropshipPurchaseService.isDropshipAvailable(order.getStoreId(), provider)
                ? Optional.of(provider)
                : Optional.empty();
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
