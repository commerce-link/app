package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DropshipEligibility {

    private final DropshipPurchaseService dropshipPurchaseService;

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
        boolean everyOtherItemSettled = orderItems.stream()
                .filter(item -> !item.isInAllocation())
                .allMatch(OrderItem::isDelivered);
        if (!everyOtherItemSettled) {
            return Optional.empty();
        }
        return dropshipPurchaseService.isDropshipAvailable(order.getStoreId(), provider)
                ? Optional.of(provider)
                : Optional.empty();
    }
}
