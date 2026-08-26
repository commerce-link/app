package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DropshipOrderLocator {

    private final OrderItemsRepository orderItemsRepository;

    public Optional<String> locate(String deliveryId) {
        Set<String> orderIds = orderItemsRepository.findByClaimedDeliveryId(deliveryId).stream()
                .map(OrderItem::getOrderId)
                .collect(Collectors.toSet());
        if (orderIds.size() > 1) {
            throw new IllegalStateException(
                    "Dropship delivery " + deliveryId + " is claimed by orders " + orderIds);
        }
        return orderIds.stream().findFirst();
    }
}
